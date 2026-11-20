package com.example.crm.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.crm.common.TimePolicy;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class ApiTestSupport {

    private static final Path DATABASE = createTempDatabasePath();
    private static final AtomicLong BIN_SEQUENCE = new AtomicLong(1);
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
    private static final ObjectMapper JSON = new ObjectMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("crm.database-path", DATABASE::toString);
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.update("DELETE FROM calls");
        jdbc.update("DELETE FROM meeting_history");
        jdbc.update("DELETE FROM meetings");
        jdbc.update("DELETE FROM companies");
    }

    protected Response get(String path) {
        return send("GET", path, null);
    }

    protected Response post(String path, Object body) {
        return send("POST", path, toJson(body));
    }

    protected Response postRaw(String path, String rawBody) {
        return send("POST", path, rawBody);
    }

    protected Response patch(String path, Object body) {
        return send("PATCH", path, toJson(body));
    }

    protected long createCompany(String name) {
        String bin = "%012d".formatted(BIN_SEQUENCE.getAndIncrement());
        Response response = post("/companies", Map.of("name", name, "bin", bin, "phone", "+7 701 000 00 00"));
        assertThat(response.status()).as(response.body().toString()).isEqualTo(201);
        return response.body().get("id").asLong();
    }

    protected Response book(long companyId, long managerId, String startAt) {
        return post("/meetings", Map.of("company_id", companyId, "manager_id", managerId, "start_at", startAt));
    }

    protected Response cancel(long meetingId) {
        return patch("/meetings/" + meetingId, Map.of("status", "cancelled"));
    }

    protected Response reschedule(long meetingId, String startAt) {
        return patch("/meetings/" + meetingId, Map.of("start_at", startAt));
    }

    protected static LocalDate nextMonday() {
        return mondayInWeeks(1);
    }

    protected static LocalDate mondayInWeeks(int weeksAhead) {
        LocalDate today = LocalDate.now(TimePolicy.BUSINESS_ZONE);
        return today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(weeksAhead);
    }

    protected static String almaty(LocalDate day, String hhmm) {
        ZonedDateTime local = ZonedDateTime.of(day, LocalTime.parse(hhmm), TimePolicy.BUSINESS_ZONE);
        return TimePolicy.format(local.toInstant());
    }

    private Response send(String method, String path, String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .method(method, body == null ? BodyPublishers.noBody() : BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = HTTP.send(request, BodyHandlers.ofString());
            JsonNode json = response.body().isBlank() ? JSON.nullNode() : JSON.readTree(response.body());
            return new Response(response.statusCode(), json);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static String toJson(Object body) {
        try {
            return JSON.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static Path createTempDatabasePath() {
        try {
            return Files.createTempDirectory("crm-test").resolve("test.db");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public record Response(int status, JsonNode body) {

        public long id() {
            return body.get("id").asLong();
        }

        public String errorCode() {
            return body.path("error").path("code").asText();
        }

        public String errorMessage() {
            return body.path("error").path("message").asText();
        }

        public String firstErrorField() {
            return body.path("error").path("details").path(0).path("field").asText();
        }
    }
}
