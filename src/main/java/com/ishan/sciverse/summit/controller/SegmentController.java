package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.service.DelegateService;
import com.ishan.sciverse.summit.service.SegmentService;
import com.ishan.sciverse.summit.service.SessionService;
import com.ishan.sciverse.summit.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Live-segment (Candor/Kolloquium) announcements. Control endpoints are
 * chair-only (see SecurityConfig); status resolves the caller's own session
 * so both chair and delegates can poll it.
 */
@Controller
public class SegmentController {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private SegmentService segmentService;

    @Autowired
    private DelegateService delegateService;

    @Autowired
    private com.ishan.sciverse.summit.repository.UserRepository userRepository;

    @PostMapping("/api/segment/control/start")
    @ResponseBody
    public Map<String, Object> start(@RequestParam String type,
                                     @RequestParam int seconds,
                                     @RequestParam(required = false) String label) {
        Optional<Session> sessionOpt = chairSession();
        if (sessionOpt.isEmpty()) {
            return error("No active session yet.");
        }
        return segmentService.start(sessionOpt.get(), type, seconds, label);
    }

    @PostMapping("/api/segment/control/pause")
    @ResponseBody
    public Map<String, Object> pause(@RequestParam int remainingSecs) {
        Optional<Session> sessionOpt = chairSession();
        if (sessionOpt.isEmpty()) {
            return error("No active session yet.");
        }
        return segmentService.pause(sessionOpt.get(), remainingSecs);
    }

    @PostMapping("/api/segment/control/resume")
    @ResponseBody
    public Map<String, Object> resume() {
        Optional<Session> sessionOpt = chairSession();
        if (sessionOpt.isEmpty()) {
            return error("No active session yet.");
        }
        return segmentService.resume(sessionOpt.get());
    }

    @PostMapping("/api/segment/control/stop")
    @ResponseBody
    public Map<String, Object> stop() {
        Optional<Session> sessionOpt = chairSession();
        if (sessionOpt.isEmpty()) {
            return error("No active session yet.");
        }
        return segmentService.stop(sessionOpt.get());
    }

    @GetMapping("/api/segment/status")
    @ResponseBody
    public Map<String, Object> status(Principal principal) {
        if (principal == null) {
            return Map.of("live", false);
        }
        boolean chair = userRepository.findByUsername(principal.getName())
                .map(u -> UserService.isChairRole(u.getRole()))
                .orElse(false);
        Optional<Session> sessionOpt = chair
                ? chairSession()
                : delegateService.getJoinedSession(principal.getName());
        if (sessionOpt.isEmpty()) {
            return Map.of("live", false);
        }
        return segmentService.status(sessionOpt.get());
    }

    private Optional<Session> chairSession() {
        return sessionService.getActiveSession()
                .or(() -> sessionService.getLatestSession());
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("success", false);
        err.put("live", false);
        err.put("message", message);
        return err;
    }
}
