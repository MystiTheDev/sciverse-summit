package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.service.LiveEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class LiveEventsController {

    @Autowired
    private LiveEventService liveEventService;

    @GetMapping(value = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events() {
        return liveEventService.connect();
    }
}
