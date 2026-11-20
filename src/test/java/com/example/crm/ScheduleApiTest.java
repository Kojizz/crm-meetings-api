package com.example.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.crm.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class ScheduleApiTest extends ApiTestSupport {

    @Test
    void scheduleReturnsOnlyActiveMeetingsOfThatLocalDay() {
        LocalDate monday = nextMonday();
        LocalDate tuesday = monday.plusDays(1);
        book(createCompany("Late"), 1, almaty(monday, "15:00"));
        book(createCompany("Early"), 1, almaty(monday, "09:00"));
        long cancelledId = book(createCompany("Cancelled"), 1, almaty(monday, "12:00")).id();
        cancel(cancelledId);
        book(createCompany("Other manager"), 2, almaty(monday, "10:00"));
        book(createCompany("Tuesday"), 1, almaty(tuesday, "09:00"));

        Response response = get("/managers/1/schedule?date=" + monday);

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("timezone").asText()).isEqualTo("Asia/Almaty");
        assertThat(field(response.body(), "company_name")).containsExactly("Early", "Late");
    }

    @Test
    void scheduleUsesAlmatyDayNotUtcDay() {
        LocalDate tuesday = nextMonday().plusDays(1);
        String start = almaty(tuesday, "09:00");
        book(createCompany("A"), 1, start);

        Response tuesdaySchedule = get("/managers/1/schedule?date=" + tuesday);
        Response mondaySchedule = get("/managers/1/schedule?date=" + tuesday.minusDays(1));

        assertThat(field(tuesdaySchedule.body(), "start_at")).containsExactly(start);
        assertThat(mondaySchedule.body().get("meetings").size()).isZero();
    }

    @Test
    void scheduleErrors() {
        assertThat(get("/managers/999/schedule?date=2030-01-07").status()).isEqualTo(404);
        assertThat(get("/managers/1/schedule").status()).isEqualTo(422);
        assertThat(get("/managers/1/schedule?date=07.01.2030").status()).isEqualTo(422);
        assertThat(get("/unknown").status()).isEqualTo(404);
    }

    private static List<String> field(JsonNode schedule, String name) {
        return StreamSupport.stream(schedule.get("meetings").spliterator(), false)
                .map(item -> item.get(name).asText())
                .toList();
    }
}
