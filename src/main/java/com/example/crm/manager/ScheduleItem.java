package com.example.crm.manager;

public record ScheduleItem(long meetingId, long companyId, String companyName, String startAt, String endAt) {
}
