package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.entity.DelegateMembership;
import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.repository.DelegateMembershipRepository;
import com.ishan.sciverse.summit.service.DelegateService;
import com.ishan.sciverse.summit.service.LiveEventService;
import com.ishan.sciverse.summit.service.NotificationService;
import com.ishan.sciverse.summit.service.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Notifies a delegate when the chair queues them for speaking.
 * The delegate portal already listens for {@code notif.changed} via SSE
 * (notif-banner.js) which plays a chime + banner, so creating a
 * per-delegate notification is enough to alert them to get ready.
 */
@Controller
public class SpeakerQueueController {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private DelegateService delegateService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private LiveEventService liveEventService;

    @Autowired
    private DelegateMembershipRepository membershipRepository;

    private static final Logger log = LoggerFactory.getLogger(SpeakerQueueController.class);

    @PostMapping("/api/speakers/notify-queued")
    @ResponseBody
    public Map<String, Object> notifyQueued(@RequestParam String name,
                                            @RequestParam(required = false, defaultValue = "QUEUED") String stage,
                                            @RequestParam(required = false) Integer position) {
        Map<String, Object> out = new LinkedHashMap<>();
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            out.put("success", false);
            out.put("message", "Delegate name is required.");
            return out;
        }
        Optional<Session> sessionOpt = sessionService.getActiveSession()
                .or(() -> sessionService.getLatestSession());
        if (sessionOpt.isEmpty()) {
            out.put("success", false);
            out.put("message", "No active session yet.");
            return out;
        }
        Session session = sessionOpt.get();
        String normalizedStage = stage == null ? "QUEUED" : stage.trim().toUpperCase();
        String title;
        String message;
        switch (normalizedStage) {
            case "NOW":
                title = "You're speaking now";
                message = trimmed + ", you are now speaking. Please take the floor.";
                break;
            case "NEXT":
                title = "You're next to speak";
                message = trimmed + ", you are next in the speaking queue. Get ready — you'll be called up momentarily.";
                break;
            case "ENDED":
                title = "Your Time is Up!";
                message = trimmed + ", the chair has ended your speaking time. Thank you!";
                normalizedStage = "ENDED";
                break;
            default:
                normalizedStage = "QUEUED";
                title = "You've been queued to speak";
                message = trimmed + ", you have been queued up for speaking"
                        + (position != null && position > 0 ? " (#" + position + " in line)" : "")
                        + ". Get ready — you'll be called up soon.";
                break;
        }

        // Match the queued display name (a Presentation/delegate-list name) to the
        // joined account. Chairs often queue short names ("shon") while the account
        // holds a fuller name or a different username, so try in order:
        // 1) exact fullName (case-insensitive), 2) exact username, 3) contains-match.
        DelegateMembership membership = null;
        String key = trimmed.toLowerCase();
        List<DelegateMembership> memberships = membershipRepository.findBySession(session);
        for (DelegateMembership m : memberships) {
            if (m.getUser() != null && m.getUser().getFullName() != null
                    && m.getUser().getFullName().trim().equalsIgnoreCase(trimmed)) {
                membership = m;
                break;
            }
        }
        if (membership == null) {
            for (DelegateMembership m : memberships) {
                if (m.getUser() != null && m.getUser().getUsername() != null
                        && m.getUser().getUsername().trim().equalsIgnoreCase(trimmed)) {
                    membership = m;
                    break;
                }
            }
        }
        if (membership == null) {
            for (DelegateMembership m : memberships) {
                if (m.getUser() == null) continue;
                String fn = m.getUser().getFullName() == null ? "" : m.getUser().getFullName().toLowerCase();
                String un = m.getUser().getUsername() == null ? "" : m.getUser().getUsername().toLowerCase();
                if ((!fn.isEmpty() && (fn.contains(key) || key.contains(fn)))
                        || (!un.isEmpty() && (un.contains(key) || key.contains(un)))) {
                    membership = m;
                    break;
                }
            }
        }
        if (membership == null || membership.getUser() == null) {
            // Delegate hasn't joined via code (offline/training) — nothing to push to,
            // but still broadcast a lightweight queue event for any live listeners.
            log.warn("speaker queue notify: '{}' not matched to any joined member in session {} ({} joined)",
                    trimmed, session.getId(), memberships.size());
            liveEventService.publish("speaker.queued", trimmed);
            out.put("success", true);
            out.put("notified", false);
            out.put("message", "Delegate has not joined; queue event broadcast only.");
            return out;
        }
        // Link-free by design: speaker alerts are banner-only, no View button,
        // so there is nothing for the wrong role to tap through to.
        notificationService.notifyUser(
                membership.getUser(),
                title,
                message,
                "SPEAKER",
                null);
        log.info("speaker queue notify: '{}' -> user '{}' stage={}", trimmed,
                membership.getUser().getUsername(), normalizedStage);
        liveEventService.publish("speaker.queued", trimmed);
        out.put("success", true);
        out.put("notified", true);
        return out;
    }
}
