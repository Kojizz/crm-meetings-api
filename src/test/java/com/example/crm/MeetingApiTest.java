package com.example.crm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.crm.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class MeetingApiTest extends ApiTestSupport {

    private final LocalDate monday = nextMonday();

    @Test
    void backToBackMeetingsOfOneManagerAreNotAConflict() {
        Response first = book(createCompany("A"), 1, almaty(monday, "10:00"));
        Response second = book(createCompany("B"), 1, almaty(monday, "11:00"));

        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);
        assertThat(first.body().get("end_at").asText()).isEqualTo(second.body().get("start_at").asText());
    }

    @Test
    void overlappingMeetingsOfOneManagerAreRejected() {
        book(createCompany("A"), 1, almaty(monday, "10:00"));

        Response overlapAfter = book(createCompany("B"), 1, almaty(monday, "10:30"));
        Response overlapBefore = book(createCompany("C"), 1, almaty(monday, "09:30"));
        Response otherManager = book(createCompany("D"), 2, almaty(monday, "10:30"));

        assertThat(overlapAfter.status()).isEqualTo(409);
        assertThat(overlapAfter.errorMessage()).contains("Manager 1");
        assertThat(overlapBefore.status()).isEqualTo(409);
        assertThat(otherManager.status()).isEqualTo(201);
    }

    @Test
    void companyCannotHaveTwoOverlappingMeetingsEvenWithDifferentManagers() {
        long companyId = createCompany("A");
        book(companyId, 1, almaty(monday, "10:00"));

        Response clash = book(companyId, 2, almaty(monday, "10:30"));
        Response later = book(companyId, 2, almaty(monday, "11:00"));

        assertThat(clash.status()).isEqualTo(409);
        assertThat(clash.errorMessage()).contains("Company " + companyId);
        assertThat(later.status()).isEqualTo(201);
    }

    @Test
    void rescheduleKeepsOriginalTimeAndHistory() {
        long meetingId = book(createCompany("A"), 1, almaty(monday, "10:00")).id();

        reschedule(meetingId, almaty(monday, "14:00"));
        Response response = reschedule(meetingId, almaty(monday, "15:00"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("start_at").asText()).isEqualTo(almaty(monday, "15:00"));
        assertThat(response.body().get("original_start_at").asText()).isEqualTo(almaty(monday, "10:00"));
        assertThat(history(response.body())).containsExactly(
                "created " + almaty(monday, "10:00"),
                "rescheduled " + almaty(monday, "14:00"),
                "rescheduled " + almaty(monday, "15:00"));
    }

    @Test
    void rescheduleIntoBusySlotIsRejectedAndMeetingIsUnchanged() {
        book(createCompany("A"), 1, almaty(monday, "12:00"));
        long meetingId = book(createCompany("B"), 1, almaty(monday, "10:00")).id();

        Response response = reschedule(meetingId, almaty(monday, "12:30"));

        assertThat(response.status()).isEqualTo(409);
        Response current = get("/meetings/" + meetingId);
        assertThat(current.body().get("start_at").asText()).isEqualTo(almaty(monday, "10:00"));
        assertThat(current.body().get("history").size()).isEqualTo(1);
    }

    @Test
    void rescheduleOverlappingItsOwnOldSlotIsAllowed() {
        long meetingId = book(createCompany("A"), 1, almaty(monday, "10:00")).id();

        assertThat(reschedule(meetingId, almaty(monday, "10:30")).status()).isEqualTo(200);
    }

    @Test
    void cancelFreesSlotAndCancelledMeetingIsImmutable() {
        long meetingId = book(createCompany("A"), 1, almaty(monday, "10:00")).id();

        Response cancelled = cancel(meetingId);
        Response rebook = book(createCompany("B"), 1, almaty(monday, "10:00"));
        Response changeCancelled = reschedule(meetingId, almaty(monday, "15:00"));

        assertThat(cancelled.status()).isEqualTo(200);
        assertThat(history(cancelled.body()).getLast()).startsWith("cancelled");
        assertThat(rebook.status()).isEqualTo(201);
        assertThat(changeCancelled.status()).isEqualTo(409);
    }

    @Test
    void patchRequiresExactlyOneAction() {
        long meetingId = book(createCompany("A"), 1, almaty(monday, "10:00")).id();

        Response both = patch("/meetings/" + meetingId,
                Map.of("start_at", almaty(monday, "11:00"), "status", "cancelled"));
        Response empty = patch("/meetings/" + meetingId, Map.of());
        Response wrongStatus = patch("/meetings/" + meetingId, Map.of("status", "done"));

        assertThat(both.status()).isEqualTo(422);
        assertThat(empty.status()).isEqualTo(422);
        assertThat(wrongStatus.status()).isEqualTo(422);
    }

    @Test
    void unknownReferencesReturn404() {
        assertThat(book(999_999, 1, almaty(monday, "10:00")).status()).isEqualTo(404);
        assertThat(book(createCompany("A"), 999, almaty(monday, "10:00")).status()).isEqualTo(404);
        assertThat(cancel(999_999).status()).isEqualTo(404);
        assertThat(get("/meetings/999999").status()).isEqualTo(404);
    }

    @Test
    void concurrentBookingOfSameSlotCreatesOnlyOneMeeting() throws Exception {
        String slot = almaty(monday, "10:00");
        List<Long> companyIds = IntStream.range(0, 8).mapToObj(i -> createCompany("C" + i)).toList();
        ExecutorService pool = Executors.newFixedThreadPool(companyIds.size());
        CountDownLatch startSignal = new CountDownLatch(1);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (long companyId : companyIds) {
                futures.add(pool.submit(() -> {
                    startSignal.await();
                    return book(companyId, 1, slot).status();
                }));
            }
            startSignal.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> future : futures) {
                statuses.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 409, 409, 409, 409, 409, 409, 409);
        } finally {
            pool.shutdownNow();
        }
    }

    private static List<String> history(JsonNode meeting) {
        return StreamSupport.stream(meeting.get("history").spliterator(), false)
                .map(entry -> entry.get("action").asText() + " " + entry.get("start_at").asText())
                .toList();
    }
}
