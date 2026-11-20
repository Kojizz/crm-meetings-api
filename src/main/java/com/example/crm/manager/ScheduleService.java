package com.example.crm.manager;

import com.example.crm.common.TimePolicy;
import com.example.crm.meeting.MeetingService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class ScheduleService {

    private static final RowMapper<ScheduleItem> ITEM_MAPPER = (rs, rowNum) -> new ScheduleItem(
            rs.getLong("meeting_id"),
            rs.getLong("company_id"),
            rs.getString("company_name"),
            rs.getString("start_at"),
            rs.getString("end_at"));

    private final JdbcTemplate jdbc;
    private final MeetingService meetingService;

    public ScheduleService(JdbcTemplate jdbc, MeetingService meetingService) {
        this.jdbc = jdbc;
        this.meetingService = meetingService;
    }

    public ManagerSchedule forDay(long managerId, LocalDate day) {
        String managerName = meetingService.requireManager(managerId);
        TimePolicy.DayBounds bounds = TimePolicy.dayBoundsUtc(day);
        List<ScheduleItem> meetings = jdbc.query("""
                SELECT m.id AS meeting_id, m.company_id, c.name AS company_name, m.start_at, m.end_at
                FROM meetings m
                JOIN companies c ON c.id = m.company_id
                WHERE m.manager_id = ? AND m.status = 'scheduled' AND m.start_at >= ? AND m.start_at < ?
                ORDER BY m.start_at
                """,
                ITEM_MAPPER, managerId, TimePolicy.format(bounds.start()), TimePolicy.format(bounds.end()));
        return new ManagerSchedule(managerId, managerName, day.toString(), TimePolicy.BUSINESS_ZONE.getId(), meetings);
    }
}
