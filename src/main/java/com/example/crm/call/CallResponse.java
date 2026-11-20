package com.example.crm.call;

public record CallResponse(
        long id,
        long companyId,
        String result,
        Long meetingId,
        String comment,
        String calledAt
) {
}
