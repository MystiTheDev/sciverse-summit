package com.ishan.sciverse.summit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class LiveEventService {

    private static final Logger log = LoggerFactory.getLogger(LiveEventService.class);

    private final Set<SseEmitter> emitters = ConcurrentHashMap.newKeySet();

    public SseEmitter connect() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException | IllegalStateException ignored) {
            emitters.remove(emitter);
        }
        return emitter;
    }

    public void publish(String type) {
        publish(type, "");
    }

    public void publish(String type, String payload) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(type).data(payload == null ? "" : payload));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }

    @Scheduled(fixedDelayString = "${notification.heartbeat-interval-ms:10000}")
    public void sendHeartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        Iterator<SseEmitter> it = emitters.iterator();
        while (it.hasNext()) {
            SseEmitter emitter = it.next();
            try {
                emitter.send(SseEmitter.event().name(":heartbeat").data(""));
            } catch (IOException | IllegalStateException e) {
                it.remove();
                log.debug("Removed dead SSE emitter during heartbeat");
            }
        }
    }

    public int connectionCount() {
        return emitters.size();
    }
}
