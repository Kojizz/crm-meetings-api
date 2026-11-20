package com.example.crm.meeting;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/meetings")
public class MeetingController {

    private final MeetingService meetingService;

    public MeetingController(MeetingService meetingService) {
        this.meetingService = meetingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeetingResponse create(@Valid @RequestBody CreateMeetingRequest request) {
        return meetingService.create(request);
    }

    @GetMapping("/{id}")
    public MeetingResponse get(@PathVariable("id") long id) {
        return meetingService.get(id);
    }

    @PatchMapping("/{id}")
    public MeetingResponse update(@PathVariable("id") long id, @RequestBody UpdateMeetingRequest request) {
        return meetingService.update(id, request);
    }
}
