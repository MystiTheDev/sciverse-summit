package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.repository.SessionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Server-side live-segment state (Candor / Kolloquium timers announced to
 * delegates). The chair's page timer stays the visual source of truth, but
 * every start/pause/resume/stop is mirrored here so delegates can render a
 * live banner + countdown. Expiry is lazy: a past endsAt reads as NONE.
 */
@Service
public class SegmentService {

    private static final int MIN_SECS = 5;
    private static final int MAX_SECS = 3600;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private LiveEventService liveEventService;

    @Autowired
    private AuditService auditService;

    public Map<String, Object> start(Session session, String type, int seconds, String label) {
        String t = type == null ? "" : type.trim().toUpperCase();
        if (!"CANDOR".equals(t) && !"KOLLOQUIUM".equals(t)) {
            return error("Unknown segment type.");
        }
        int secs = Math.min(MAX_SECS, Math.max(MIN_SECS, seconds));
        session.setSegmentType(t);
        session.setSegmentLabel(label == null || label.isBlank() ? defaultLabel(t) : label.trim());
        session.setSegmentTotalSecs(secs);
        session.setSegmentEndsAt(LocalDateTime.now().plusSeconds(secs));
        session.setSegmentPaused(false);
        session.setSegmentRemainingSecs(secs);
        sessionRepository.save(session);
        liveEventService.publish("segment.changed", "");
        auditService.log("SEGMENT_STARTED", "SESSION", session.getId(), session.getId(),
                "Started " + t + " segment (" + secs + "s).");
        Map<String, Object> out = status(session);
        out.put("success", true);
        return out;
    }

    public Map<String, Object> pause(Session session, int remainingSecs) {
        if (!isLiveType(session.getSegmentType())) {
            return error("No live segment to pause.");
        }
        session.setSegmentPaused(true);
        session.setSegmentRemainingSecs(Math.max(0, remainingSecs));
        session.setSegmentEndsAt(null);
        sessionRepository.save(session);
        liveEventService.publish("segment.changed", "");
        Map<String, Object> out = status(session);
        out.put("success", true);
        return out;
    }

    public Map<String, Object> resume(Session session) {
        if (!isLiveType(session.getSegmentType()) || !Boolean.TRUE.equals(session.getSegmentPaused())) {
            return error("No paused segment to resume.");
        }
        int remaining = session.getSegmentRemainingSecs() == null ? 0 : session.getSegmentRemainingSecs();
        if (remaining <= 0) {
            return stop(session);
        }
        session.setSegmentPaused(false);
        session.setSegmentEndsAt(LocalDateTime.now().plusSeconds(remaining));
        sessionRepository.save(session);
        liveEventService.publish("segment.changed", "");
        Map<String, Object> out = status(session);
        out.put("success", true);
        return out;
    }

    public Map<String, Object> stop(Session session) {
        String stopped = session.getSegmentType();
        clear(session);
        sessionRepository.save(session);
        liveEventService.publish("segment.changed", "");
        auditService.log("SEGMENT_STOPPED", "SESSION", session.getId(), session.getId(),
                "Stopped " + stopped + " segment.");
        Map<String, Object> out = status(session);
        out.put("success", true);
        return out;
    }

    /** Read model for the delegate banner + mini popup. Never throws. */
    public Map<String, Object> status(Session session) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (session == null || !isLiveType(session.getSegmentType())) {
            out.put("live", false);
            return out;
        }
        if (Boolean.TRUE.equals(session.getSegmentPaused())) {
            int remaining = session.getSegmentRemainingSecs() == null ? 0 : session.getSegmentRemainingSecs();
            if (remaining <= 0) {
                clear(session);
                sessionRepository.save(session);
                out.put("live", false);
                return out;
            }
            out.put("live", true);
            out.put("paused", true);
            putCommon(session, out, remaining);
            return out;
        }
        if (session.getSegmentEndsAt() == null) {
            out.put("live", false);
            return out;
        }
        long remaining = ChronoUnit.SECONDS.between(LocalDateTime.now(), session.getSegmentEndsAt());
        if (remaining <= 0) {
            // Lazy expiry: chair's timer hit zero without a stop call.
            clear(session);
            sessionRepository.save(session);
            out.put("live", false);
            return out;
        }
        out.put("live", true);
        out.put("paused", false);
        putCommon(session, out, (int) remaining);
        return out;
    }

    private void putCommon(Session session, Map<String, Object> out, int remaining) {
        out.put("type", session.getSegmentType());
        out.put("label", session.getSegmentLabel() == null ? defaultLabel(session.getSegmentType()) : session.getSegmentLabel());
        out.put("totalSecs", session.getSegmentTotalSecs() == null ? remaining : session.getSegmentTotalSecs());
        out.put("remainingSecs", remaining);
    }

    private boolean isLiveType(String t) {
        return "CANDOR".equalsIgnoreCase(t) || "KOLLOQUIUM".equalsIgnoreCase(t);
    }

    private String defaultLabel(String t) {
        return "KOLLOQUIUM".equalsIgnoreCase(t) ? "Kolloquium" : "Candor Session";
    }

    private void clear(Session session) {
        session.setSegmentType("NONE");
        session.setSegmentLabel(null);
        session.setSegmentTotalSecs(null);
        session.setSegmentEndsAt(null);
        session.setSegmentPaused(false);
        session.setSegmentRemainingSecs(null);
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("success", false);
        err.put("live", false);
        err.put("message", message);
        return err;
    }
}
