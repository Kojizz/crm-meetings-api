package com.example.crm.call;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateCallRequest(
        @JsonProperty("company_id")
        @NotNull
        @Positive
        Long companyId,

        @JsonProperty("result")
        @NotNull
        @Pattern(regexp = "no_answer|refused|meeting_set", message = "must be one of: no_answer, refused, meeting_set")
        String result,

        @JsonProperty("meeting_id")
        @Positive
        Long meetingId,

        @JsonProperty("comment")
        @Size(max = 1000)
        String comment
) {
}
