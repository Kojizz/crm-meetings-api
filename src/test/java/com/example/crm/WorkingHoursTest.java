package com.example.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.crm.support.ApiTestSupport;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class WorkingHoursTest extends ApiTestSupport {

    @ParameterizedTest(name = "{3}")
    @CsvSource({
            "0, 09:00, 201, monday 09:00 is the first slot",
            "0, 17:00, 201, monday 17:00 ends exactly at 18:00",
            "0, 08:59, 422, monday 08:59 starts before opening",
            "0, 17:01, 422, monday 17:01 ends after closing",
            "0, 17:30, 422, monday 17:30 from the spec",
            "0, 18:00, 422, monday 18:00 is after closing",
            "4, 17:00, 201, friday 17:00 is the last slot of the week",
            "5, 10:00, 422, saturday",
            "6, 10:00, 422, sunday"
    })
    void workingDayBoundaries(int dayOffset, String start, int expectedStatus, String description) {
        long companyId = createCompany("ТОО Тест");
        LocalDate day = nextMonday().plusDays(dayOffset);

        Response response = book(companyId, 1, almaty(day, start));

        assertThat(response.status()).as(response.body().toString()).isEqualTo(expectedStatus);
        if (expectedStatus == 422) {
            assertThat(response.errorCode()).isEqualTo("business_rule_violation");
        }
    }

    @Test
    void workingHoursAreCheckedInAlmatyNotUtc() {
        String monday = nextMonday().toString();

        Response almaty0900 = book(createCompany("A"), 1, monday + "T04:00:00Z");
        Response almaty1830 = book(createCompany("B"), 2, monday + "T13:30:00Z");

        assertThat(almaty0900.status()).isEqualTo(201);
        assertThat(almaty1830.status()).isEqualTo(422);
        assertThat(almaty1830.errorMessage()).contains("18:30-19:30");
    }

    @Test
    void nonUtcOffsetIsAcceptedAndReturnedInUtc() {
        LocalDate monday = nextMonday();

        Response response = book(createCompany("A"), 1, monday + "T10:00:00+05:00");

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.body().get("start_at").asText()).isEqualTo(almaty(monday, "10:00"));
    }

    @Test
    void dateTimeWithoutTimezoneIsRejected() {
        Response response = book(createCompany("A"), 1, nextMonday() + "T10:00:00");

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.firstErrorField()).isEqualTo("start_at");
    }

    @Test
    void startWithSecondsIsRejected() {
        Response response = book(createCompany("A"), 1, nextMonday() + "T05:00:30Z");

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.firstErrorField()).isEqualTo("start_at");
    }

    @Test
    void meetingInThePastIsRejected() {
        Response response = book(createCompany("A"), 1, almaty(mondayInWeeks(-1), "10:00"));

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.errorMessage()).contains("future");
    }
}
