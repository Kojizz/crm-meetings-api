package com.example.crm.common;

public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String message) {
        super(422, "business_rule_violation", message);
    }
}
