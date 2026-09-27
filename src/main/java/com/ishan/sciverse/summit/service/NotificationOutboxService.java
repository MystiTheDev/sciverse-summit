package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.entity.Notification;
import com.ishan.sciverse.summit.entity.User;
import com.ishan.sciverse.summit.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Retry queue for notifications that could not be persisted on the first
 * attempt.
 *
 * <p>Previously the "retry" did no delivery at all: it flipped
 * {@code FAILED -> SENT} and published an empty-payload refresh hint, so an
 * entry could be marked delivered without ever reaching its recipient. Now the
 * attempt actually re-saves the row and pushes the real payload to the
 * addressee, and only a genuine failure keeps the entry retryable.
 *
 * <p>Outbox rows are created with {@code read = true} on purpose: a failed
 * first attempt should not light up the unread badge for something the user
 * may never have seen.
 */
@Service
public class NotificationOutboxService {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxService.class);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private LiveEventService liveEventService;

    @Autowired
    private NotificationPayloads payloads;

    @Autowired
    private NotificationMetrics metrics;

    @Value("${notification.outbox.max-retries:5}")
    private int maxRetries;

    private static final long[] BACKOFF_MS = {1000, 5000, 30000, 300000, 300000};

    public void enqueueFailedNotification(User user, String title, String message, String type, String link) {
        try {
            Notification n = new Notification();
            n.setUser(user);
            n.setTitle(title != null ? title : "Notification");
            n.setMessage(message != null ? message : "");
            n.setType(type != null ? type : "GENERAL");
            n.setLink(link);
            n.setRead(true);
            n.setStatus("FAILED");
            n.setRetryCount(0);
            n.setNextRetryAt(LocalDateTime.now().plusSeconds(1));
            notificationRepository.save(n);
            log.info("Enqueued failed notification for retry: user={}, type={}", user.getUsername(), type);
        } catch (Exception e) {
            log.error("Failed to enqueue notification for retry: user={}, error={}", user.getUsername(), e.getMessage());
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void retryFailedNotifications() {
        LocalDateTime now = LocalDateTime.now();
        List<Notification> retryable = notificationRepository.findByStatusAndNextRetryAtBefore("FAILED", now);

        for (Notification n : retryable) {
            String who = n.getUser() != null ? n.getUser().getUsername() : "<no user>";
            try {
                if (n.getRetryCount() >= maxRetries) {
                    n.setStatus("DEAD_LETTER");
                    n.setNextRetryAt(null);
                    notificationRepository.save(n);
                    log.warn("Notification moved to dead letter: id={}, user={}, retries={}",
                            n.getId(), who, n.getRetryCount());
                    continue;
                }

                n.setStatus("RETRYING");
                n.setRetryCount(n.getRetryCount() + 1);

                // Real delivery attempt: persist, then push to the addressee.
                // Per-user routing means no other browser sees this payload.
                Notification saved = notificationRepository.save(n);
                if (saved.getUser() != null) {
                    liveEventService.publishToUser(saved.getUser().getUsername(), "notif.changed", payloads.build(saved));
                }

                saved.setStatus("SENT");
                saved.setNextRetryAt(null);
                notificationRepository.save(saved);
                metrics.incrementRetried();
                log.debug("Retry delivered: id={}, user={}, attempt={}", saved.getId(), who, saved.getRetryCount());

            } catch (Exception e) {
                // Still failing: keep it retryable and back off before the next try.
                int backoffIndex = Math.min(Math.max(n.getRetryCount() - 1, 0), BACKOFF_MS.length - 1);
                long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1, BACKOFF_MS[backoffIndex] / 10));
                n.setStatus("FAILED");
                n.setNextRetryAt(LocalDateTime.now().plus(Duration.ofMillis(BACKOFF_MS[backoffIndex] + jitter)));
                try {
                    notificationRepository.save(n);
                } catch (Exception ignored) {
                    // Nothing more we can do for this row; it will be retried
                    // from the persisted FAILED state on the next tick.
                }
                log.error("Retry failed: id={}, user={}, attempt={}, error={}",
                        n.getId(), who, n.getRetryCount(), e.getMessage());
            }
        }
    }
}
