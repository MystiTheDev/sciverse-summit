package com.ishan.sciverse.summit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-Sent Events broker.
 *
 * <p>Emitters are bound to the username that opened the connection, so
 * per-user events (notifications) reach only that user's tabs. Without that
 * binding every connected browser received every notification payload,
 * including another user's title, message and id.
 *
 * <p>Two kinds of event exist:
 * <ul>
 *   <li><b>Per-user</b> — sent with {@link #publishToUser}. Carries a real
 *       notification payload and only reaches the addressee.</li>
 *   <li><b>Global</b> — sent with {@link #publish}. Deliberately
 *       audience-free hints ("something changed, go re-fetch") that carry no
 *       user data. Every attributed client still gets them, as before.</li>
 * </ul>
 */
@Service
public class LiveEventService {

    private static final Logger log = LoggerFactory.getLogger(LiveEventService.class);

    /** Key for a connection that could not be attributed to a user. */
    private static final String ANONYMOUS = "";

    private final NotificationMetrics metrics;

    /** lower-cased username -> that user's open emitters. */
    private final Map<String, Set<SseEmitter>> byUser = new ConcurrentHashMap<>();

    public LiveEventService(NotificationMetrics metrics) {
        this.metrics = metrics;
    }

    private static String key(String username) {
        return username == null ? ANONYMOUS : username.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Opens an emitter bound to {@code username}. Connections that cannot be
     * attributed to a user still receive global events, never per-user ones.
     */
    public SseEmitter connect(String username) {
        SseEmitter emitter = new SseEmitter(0L);
        String userKey = key(username);

        Set<SseEmitter> set = byUser.computeIfAbsent(userKey, k -> ConcurrentHashMap.newKeySet());
        set.add(emitter);

        emitter.onCompletion(() -> remove(userKey, emitter));
        emitter.onTimeout(() -> remove(userKey, emitter));
        emitter.onError(e -> remove(userKey, emitter));

        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException | IllegalStateException ignored) {
            remove(userKey, emitter);
        }
        return emitter;
    }

    private void remove(String userKey, SseEmitter emitter) {
        Set<SseEmitter> set = byUser.get(userKey);
        if (set != null) {
            set.remove(emitter);
            if (set.isEmpty()) {
                byUser.remove(userKey, set);
            }
        }
    }

    /** Global event: every attributed client, no user data in the payload. */
    public void publish(String type) {
        publish(type, "");
    }

    public void publish(String type, String payload) {
        for (Map.Entry<String, Set<SseEmitter>> entry : byUser.entrySet()) {
            if (ANONYMOUS.equals(entry.getKey())) {
                continue;
            }
            send(entry.getKey(), entry.getValue(), type, payload);
        }
    }

    /**
     * Per-user event: only this user's open connections receive it. This is
     * what carries a real notification payload.
     */
    public void publishToUser(String username, String type, String payload) {
        String userKey = key(username);
        if (ANONYMOUS.equals(userKey)) {
            return;
        }
        Set<SseEmitter> set = byUser.get(userKey);
        if (set == null) {
            return;
        }
        send(userKey, set, type, payload);
    }

    private void send(String userKey, Set<SseEmitter> set, String type, String payload) {
        String data = payload == null ? "" : payload;
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(type).data(data));
                metrics.incrementSseEvents();
            } catch (IOException | IllegalStateException e) {
                remove(userKey, emitter);
                metrics.incrementSseDeadEmitters();
            }
        }
    }

    @Scheduled(fixedDelayString = "${notification.heartbeat-interval-ms:10000}")
    public void sendHeartbeat() {
        if (byUser.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Set<SseEmitter>> entry : byUser.entrySet()) {
            String userKey = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().name(":heartbeat").data(""));
                } catch (IOException | IllegalStateException e) {
                    remove(userKey, emitter);
                    metrics.incrementSseDeadEmitters();
                    log.debug("Removed dead SSE emitter during heartbeat");
                }
            }
        }
    }

    public int connectionCount() {
        return byUser.values().stream().mapToInt(Set::size).sum();
    }
}
