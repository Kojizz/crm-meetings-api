package com.example.crm.company;

public record CompanyResponse(
        long id,
        String name,
        String bin,
        String phone,
        String status,
        String createdAt
) {
}
