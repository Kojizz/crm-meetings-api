package com.example.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.crm.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class CompanyApiTest extends ApiTestSupport {

    private static final Map<String, Object> VALID_COMPANY =
            Map.of("name", "ТОО Алматы Трейд", "bin", "170340021554", "phone", "+77010000000");

    @Test
    void createCompanyReturnsNewStatus() {
        Response response = post("/companies", VALID_COMPANY);

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.body().get("status").asText()).isEqualTo("new");
        assertThat(response.body().get("bin").asText()).isEqualTo("170340021554");
        assertThat(response.body().get("created_at").asText()).endsWith("Z");
    }

    @Test
    void duplicateBinIsConflict() {
        post("/companies", VALID_COMPANY);

        Response response = post("/companies", Map.of("name", "B", "bin", "170340021554", "phone", "+77010000000"));

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorMessage()).contains("170340021554");
    }

    @Test
    void invalidBinReturnsFieldError() {
        Response response = post("/companies", Map.of("name", "A", "bin", "12345", "phone", "+77010000000"));

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.errorCode()).isEqualTo("validation_error");
        assertThat(response.firstErrorField()).isEqualTo("bin");
    }

    @Test
    void listCompaniesPaginates() {
        for (int i = 0; i < 5; i++) {
            createCompany("Company " + i);
        }

        Response response = get("/companies?limit=2&offset=2");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("total").asLong()).isEqualTo(5);
        assertThat(names(response.body().get("items"))).containsExactly("Company 2", "Company 3");
    }

    @Test
    void listCompaniesRejectsUnknownStatusAndBadLimit() {
        assertThat(get("/companies?status=unknown").status()).isEqualTo(422);
        assertThat(get("/companies?limit=0").status()).isEqualTo(422);
        assertThat(get("/companies?limit=abc").status()).isEqualTo(422);
    }

    @Test
    void malformedJsonReturns422Not500() {
        Response response = postRaw("/companies", "{bad json");

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.firstErrorField()).isEqualTo("body");
    }

    @Test
    void unknownFieldIsRejected() {
        Response response = post("/companies",
                Map.of("name", "A", "bin", "170340021554", "phone", "+77010000000", "extra", 1));

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.firstErrorField()).isEqualTo("extra");
    }

    static List<String> names(JsonNode items) {
        return StreamSupport.stream(items.spliterator(), false).map(item -> item.get("name").asText()).toList();
    }
}
