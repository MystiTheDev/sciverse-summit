package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.service.LiveEventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;

@RestController
public class LiveEventsController {

    @Autowired
    private LiveEventService liveEventService;

    /**
     * Opens an event stream bound to the authenticated user, so per-user
     * events (notifications) are only delivered to their addressee. The
     * endpoint sits behind {@code anyRequest().authenticated()}, but a null
     * principal is still tolerated and simply gets global events only.
     */
    @GetMapping(value = "/api/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(Principal principal) {
        return liveEventService.connect(principal != null ? principal.getName() : null);
    }
}
