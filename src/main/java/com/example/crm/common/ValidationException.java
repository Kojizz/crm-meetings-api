package com.example.crm.common;

import java.util.List;

public class ValidationException extends ApiException {

    public static final String MESSAGE = "Request validation failed";

    public ValidationException(List<FieldIssue> details) {
        super(422, "validation_error", MESSAGE, details);
    }

    public ValidationException(String field, String message) {
        this(List.of(new FieldIssue(field, message)));
    }
}
