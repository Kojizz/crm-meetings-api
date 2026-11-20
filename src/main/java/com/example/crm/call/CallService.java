package com.example.crm.call;

import com.example.crm.common.BusinessRuleException;
import com.example.crm.common.ConflictException;
import com.example.crm.common.TimePolicy;
import com.example.crm.company.CompanyService;
import java.time.Clock;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CallService {

    private static final String MEETING_SET = "meeting_set";

    private static final RowMapper<CallResponse> CALL_MAPPER = (rs, rowNum) -> {
        long meetingId = rs.getLong("meeting_id");
        Long nullableMeetingId = rs.wasNull() ? null : meetingId;
        return new CallResponse(
                rs.getLong("id"),
                rs.getLong("company_id"),
                rs.getString("result"),
                nullableMeetingId,
                rs.getString("comment"),
                rs.getString("called_at"));
    };

    private static final RowMapper<MeetingRef> MEETING_REF_MAPPER = (rs, rowNum) -> new MeetingRef(
            rs.getLong("company_id"),
            rs.getString("status"));

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final CompanyService companyService;

    public CallService(JdbcTemplate jdbc, Clock clock, CompanyService companyService) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.companyService = companyService;
    }

    @Transactional
    public CallResponse create(CreateCallRequest request) {
        companyService.get(request.companyId());

        if (MEETING_SET.equals(request.result())) {
            validateMeetingLink(request.companyId(), request.meetingId());
        } else if (request.meetingId() != null) {
            throw new BusinessRuleException("'meeting_id' is allowed only for result 'meeting_set', got '%s'"
                    .formatted(request.result()));
        }

        jdbc.update("INSERT INTO calls (company_id, result, meeting_id, comment, called_at) VALUES (?, ?, ?, ?, ?)",
                request.companyId(), request.result(), request.meetingId(), request.comment(),
                TimePolicy.format(clock.instant()));
        long id = jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        jdbc.update("UPDATE companies SET status = ? WHERE id = ?", request.result(), request.companyId());
        return jdbc.queryForObject("SELECT * FROM calls WHERE id = ?", CALL_MAPPER, id);
    }

    private void validateMeetingLink(long companyId, Long meetingId) {
        if (meetingId == null) {
            throw new BusinessRuleException(
                    "Call with result 'meeting_set' requires 'meeting_id' of a meeting created via POST /meetings");
        }
        MeetingRef meeting = jdbc.query("SELECT company_id, status FROM meetings WHERE id = ?",
                        MEETING_REF_MAPPER, meetingId).stream()
                .findFirst()
                .orElseThrow(() -> new BusinessRuleException("Meeting %d does not exist".formatted(meetingId)));
        if (meeting.companyId() != companyId) {
            throw new BusinessRuleException("Meeting %d belongs to company %d, not to company %d"
                    .formatted(meetingId, meeting.companyId(), companyId));
        }
        if (!"scheduled".equals(meeting.status())) {
            throw new BusinessRuleException("Meeting %d is cancelled".formatted(meetingId));
        }
        List<Long> linkedCalls = jdbc.queryForList("SELECT id FROM calls WHERE meeting_id = ?", Long.class, meetingId);
        if (!linkedCalls.isEmpty()) {
            throw new ConflictException("Meeting %d is already linked to call %d"
                    .formatted(meetingId, linkedCalls.get(0)));
        }
    }

    private record MeetingRef(long companyId, String status) {
    }
}
