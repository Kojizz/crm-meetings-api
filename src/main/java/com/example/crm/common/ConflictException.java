package com.example.crm.common;

public class ConflictException extends ApiException {

    public ConflictException(String message) {
        super(409, "conflict", message);
    }
}
