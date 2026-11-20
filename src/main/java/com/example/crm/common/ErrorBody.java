package com.example.crm.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public record ErrorBody(ApiError error) {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record ApiError(String code, String message, List<FieldIssue> details) {
    }

    public static ErrorBody of(String code, String message, List<FieldIssue> details) {
        return new ErrorBody(new ApiError(code, message, details));
    }
}
