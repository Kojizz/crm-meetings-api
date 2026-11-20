package com.example.crm.company;

import java.util.List;

public record CompanyPage(List<CompanyResponse> items, long total, int limit, int offset) {
}
