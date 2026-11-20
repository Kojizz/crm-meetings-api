package com.example.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.crm.support.ApiTestSupport;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CallApiTest extends ApiTestSupport {

    private final LocalDate monday = nextMonday();

    @Test
    void meetingSetWithoutMeetingIsRejected() {
        Response response = post("/calls", Map.of("company_id", createCompany("A"), "result", "meeting_set"));

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.errorMessage()).contains("meeting_id");
    }

    @Test
    void meetingSetWithUnknownOrForeignMeetingIsRejected() {
        long owner = createCompany("Owner");
        long other = createCompany("Other");
        long meetingId = book(owner, 1, almaty(monday, "10:00")).id();

        Response unknown = post("/calls", Map.of("company_id", owner, "result", "meeting_set", "meeting_id", 999_999));
        Response foreign = post("/calls", Map.of("company_id", other, "result", "meeting_set", "meeting_id", meetingId));

        assertThat(unknown.status()).isEqualTo(422);
        assertThat(foreign.status()).isEqualTo(422);
    }

    @Test
    void meetingSetWithCancelledMeetingIsRejected() {
        long companyId = createCompany("A");
        long meetingId = book(companyId, 1, almaty(monday, "10:00")).id();
        cancel(meetingId);

        Response response = post("/calls",
                Map.of("company_id", companyId, "result", "meeting_set", "meeting_id", meetingId));

        assertThat(response.status()).isEqualTo(422);
    }

    @Test
    void meetingIdWithOtherResultIsRejected() {
        long companyId = createCompany("A");
        long meetingId = book(companyId, 1, almaty(monday, "10:00")).id();

        Response response = post("/calls", Map.of("company_id", companyId, "result", "refused", "meeting_id", meetingId));

        assertThat(response.status()).isEqualTo(422);
    }

    @Test
    void unknownResultIsRejected() {
        Response response = post("/calls", Map.of("company_id", createCompany("A"), "result", "busy"));

        assertThat(response.status()).isEqualTo(422);
        assertThat(response.firstErrorField()).isEqualTo("result");
    }

    @Test
    void callResultUpdatesCompanyStatusForFiltering() {
        long called = createCompany("A");
        long refused = createCompany("B");
        createCompany("C");
        long meetingId = book(called, 1, almaty(monday, "10:00")).id();

        Response ok = post("/calls", Map.of("company_id", called, "result", "meeting_set", "meeting_id", meetingId));
        post("/calls", Map.of("company_id", refused, "result", "refused"));
        Response duplicateLink = post("/calls",
                Map.of("company_id", called, "result", "meeting_set", "meeting_id", meetingId));

        assertThat(ok.status()).isEqualTo(201);
        assertThat(ok.body().get("meeting_id").asLong()).isEqualTo(meetingId);
        assertThat(duplicateLink.status()).isEqualTo(409);
        assertThat(CompanyApiTest.names(get("/companies?status=meeting_set").body().get("items"))).containsExactly("A");
        assertThat(CompanyApiTest.names(get("/companies?status=refused").body().get("items"))).containsExactly("B");
        assertThat(CompanyApiTest.names(get("/companies?status=new").body().get("items"))).containsExactly("C");
    }
}
