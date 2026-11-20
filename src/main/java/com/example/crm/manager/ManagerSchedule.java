package com.example.crm.manager;

import java.util.List;

public record ManagerSchedule(
        long managerId,
        String managerName,
        String date,
        String timezone,
        List<ScheduleItem> meetings
) {
}
