package com.example.crm.meeting;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateMeetingRequest(
        @JsonProperty("start_at")
        String startAt,

        @JsonProperty("status")
        String status
) {
}
