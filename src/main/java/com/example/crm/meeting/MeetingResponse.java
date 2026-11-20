package com.example.crm.meeting;

import java.util.List;

public record MeetingResponse(
        long id,
        long companyId,
        long managerId,
        String startAt,
        String endAt,
        String status,
        String originalStartAt,
        String createdAt,
        String updatedAt,
        List<MeetingHistoryEntry> history
) {
}
