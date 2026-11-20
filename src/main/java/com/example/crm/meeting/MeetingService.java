package com.example.crm.meeting;

import static com.example.crm.common.TimePolicy.MEETING_DURATION;
import static com.example.crm.common.TimePolicy.format;

import com.example.crm.common.BusinessRuleException;
import com.example.crm.common.ConflictException;
import com.example.crm.common.NotFoundException;
import com.example.crm.common.TimePolicy;
import com.example.crm.common.ValidationException;
import com.example.crm.company.CompanyService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MeetingService {

    private static final String START_AT = "start_at";
    private static final String SCHEDULED = "scheduled";
    private static final String CANCELLED = "cancelled";

    private static final RowMapper<MeetingRow> MEETING_MAPPER = (rs, rowNum) -> new MeetingRow(
            rs.getLong("id"),
            rs.getLong("company_id"),
            rs.getLong("manager_id"),
            rs.getString("start_at"),
            rs.getString("end_at"),
            rs.getString("status"),
            rs.getString("original_start_at"),
            rs.getString("created_at"),
            rs.getString("updated_at"));

    private static final RowMapper<MeetingHistoryEntry> HISTORY_MAPPER = (rs, rowNum) -> new MeetingHistoryEntry(
            rs.getString("action"),
            rs.getString("start_at"),
            rs.getString("end_at"),
            rs.getString("changed_at"));

    private static final RowMapper<Slot> SLOT_MAPPER = (rs, rowNum) -> new Slot(
            rs.getLong("id"),
            rs.getString("start_at"),
            rs.getString("end_at"));

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CompanyService companyService;

    public MeetingService(JdbcTemplate jdbc, Clock clock, CompanyService companyService) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.companyService = companyService;
    }

    @Transactional
    public MeetingResponse create(CreateMeetingRequest request) {
        Instant start = TimePolicy.parseStartAt(START_AT, request.startAt());
        Instant end = start.plus(MEETING_DURATION);
        companyService.get(request.companyId());
        requireManager(request.managerId());
        ensureSlotAvailable(request.companyId(), request.managerId(), start, end, null);

        String now = format(clock.instant());
        String startAt = format(start);
        String endAt = format(end);
        jdbc.update("""
                INSERT INTO meetings (company_id, manager_id, start_at, end_at, original_start_at,
                                      status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, 'scheduled', ?, ?)
                """,
                request.companyId(), request.managerId(), startAt, endAt, startAt, now, now);
        long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        addHistory(id, "created", startAt, endAt, now);
        return get(id);
    }

    @Transactional
    public MeetingResponse update(long id, UpdateMeetingRequest request) {
        boolean reschedule = request.startAt() != null;
        boolean cancel = request.status() != null;
        if (reschedule == cancel) {
            throw new ValidationException("body",
                    "provide exactly one of: 'start_at' to reschedule or 'status': 'cancelled' to cancel");
        }
        if (cancel && !CANCELLED.equals(request.status())) {
            throw new ValidationException("status", "only 'cancelled' is allowed");
        }
        Instant newStart = reschedule ? TimePolicy.parseStartAt(START_AT, request.startAt()) : null;

        MeetingRow meeting = findRow(id);
        if (CANCELLED.equals(meeting.status())) {
            throw new ConflictException("Meeting %d is cancelled and cannot be changed".formatted(id));
        }
        String now = format(clock.instant());

        if (cancel) {
            jdbc.update("UPDATE meetings SET status = 'cancelled', updated_at = ? WHERE id = ?", now, id);
            addHistory(id, "cancelled", meeting.startAt(), meeting.endAt(), now);
            return get(id);
        }

        Instant newEnd = newStart.plus(MEETING_DURATION);
        String startAt = format(newStart);
        String endAt = format(newEnd);
        if (startAt.equals(meeting.startAt())) {
            throw new BusinessRuleException("Meeting %d is already scheduled at %s".formatted(id, startAt));
        }
        ensureSlotAvailable(meeting.companyId(), meeting.managerId(), newStart, newEnd, id);
        jdbc.update("UPDATE meetings SET start_at = ?, end_at = ?, updated_at = ? WHERE id = ?",
                startAt, endAt, now, id);
        addHistory(id, "rescheduled", startAt, endAt, now);
        return get(id);
    }

    public MeetingResponse get(long id) {
        MeetingRow row = findRow(id);
        List<MeetingHistoryEntry> history = jdbc.query(
                "SELECT action, start_at, end_at, changed_at FROM meeting_history WHERE meeting_id = ? ORDER BY id",
                HISTORY_MAPPER, id);
        return new MeetingResponse(row.id(), row.companyId(), row.managerId(), row.startAt(), row.endAt(),
                row.status(), row.originalStartAt(), row.createdAt(), row.updatedAt(), history);
    }

    public String requireManager(long managerId) {
        return jdbc.queryForList("SELECT name FROM managers WHERE id = ?", String.class, managerId).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Manager %d not found".formatted(managerId)));
    }

    private MeetingRow findRow(long id) {
        return jdbc.query("SELECT * FROM meetings WHERE id = ?", MEETING_MAPPER, id).stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Meeting %d not found".formatted(id)));
    }

    private void ensureSlotAvailable(long companyId, long managerId, Instant start, Instant end, Long excludeId) {
        if (!start.isAfter(clock.instant())) {
            throw new BusinessRuleException("Meeting start_at must be in the future");
        }
        TimePolicy.workingHoursViolation(start, end).ifPresent(violation -> {
            throw new BusinessRuleException(violation);
        });
        findOverlap("manager_id", managerId, start, end, excludeId).ifPresent(clash -> {
            throw new ConflictException("Manager %d already has meeting %d from %s to %s"
                    .formatted(managerId, clash.id(), clash.startAt(), clash.endAt()));
        });
        findOverlap("company_id", companyId, start, end, excludeId).ifPresent(clash -> {
            throw new ConflictException("Company %d already has an active meeting %d from %s to %s"
                    .formatted(companyId, clash.id(), clash.startAt(), clash.endAt()));
        });
    }

    private Optional<Slot> findOverlap(String ownerColumn, long ownerId, Instant start, Instant end, Long excludeId) {
        String sql = """
                SELECT id, start_at, end_at FROM meetings
                WHERE %s = ? AND status = 'scheduled' AND id != ? AND start_at < ? AND end_at > ?
                ORDER BY start_at LIMIT 1
                """.formatted(ownerColumn);
        long excluded = excludeId == null ? 0L : excludeId;
        return jdbc.query(sql, SLOT_MAPPER, ownerId, excluded, format(end), format(start)).stream().findFirst();
    }

    private void addHistory(long meetingId, String action, String startAt, String endAt, String changedAt) {
        jdbc.update("""
                INSERT INTO meeting_history (meeting_id, action, start_at, end_at, changed_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                meetingId, action, startAt, endAt, changedAt);
    }

    private record MeetingRow(long id, long companyId, long managerId, String startAt, String endAt, String status,
                              String originalStartAt, String createdAt, String updatedAt) {
    }

    private record Slot(long id, String startAt, String endAt) {
    }
}
