package com.ishan.sciverse.summit.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class NotificationMetrics {

    private static final Logger log = LoggerFactory.getLogger(NotificationMetrics.class);

    private final AtomicLong created = new AtomicLong(0);
    private final AtomicLong failed = new AtomicLong(0);
    private final AtomicLong retried = new AtomicLong(0);
    private final AtomicLong cleaned = new AtomicLong(0);
    private final AtomicLong sseEvents = new AtomicLong(0);
    private final AtomicLong sseDeadEmitters = new AtomicLong(0);
    private final AtomicLong bannerShown = new AtomicLong(0);
    private final AtomicLong bannerQueued = new AtomicLong(0);

    public void incrementCreated() { created.incrementAndGet(); }
    public void incrementFailed() { failed.incrementAndGet(); }
    public void incrementRetried() { retried.incrementAndGet(); }
    public void incrementCleaned() { cleaned.incrementAndGet(); }
    public void incrementSseEvents() { sseEvents.incrementAndGet(); }
    public void incrementSseDeadEmitters() { sseDeadEmitters.incrementAndGet(); }
    public void incrementBannerShown() { bannerShown.incrementAndGet(); }
    public void incrementBannerQueued() { bannerQueued.incrementAndGet(); }

    @Scheduled(fixedDelay = 300000)
    public void logMetrics() {
        log.info("NotificationMetrics — created={}, failed={}, retried={}, cleaned={}, sseEvents={}, deadEmitters={}, bannerShown={}, bannerQueued={}",
                created.get(), failed.get(), retried.get(), cleaned.get(),
                sseEvents.get(), sseDeadEmitters.get(),
                bannerShown.get(), bannerQueued.get());
    }
}
