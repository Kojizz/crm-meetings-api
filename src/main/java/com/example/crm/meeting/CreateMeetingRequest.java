package com.example.crm.meeting;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateMeetingRequest(
        @JsonProperty("company_id")
        @NotNull
        @Positive
        Long companyId,

        @JsonProperty("manager_id")
        @NotNull
        @Positive
        Long managerId,

        @JsonProperty("start_at")
        @NotBlank
        String startAt
) {
}
