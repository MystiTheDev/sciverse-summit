package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.entity.Notification;
import com.ishan.sciverse.summit.entity.User;
import com.ishan.sciverse.summit.repository.NotificationRepository;
import com.ishan.sciverse.summit.repository.UserRepository;
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

@Service
public class NotificationOutboxService {

    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxService.class);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LiveEventService liveEventService;

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
            try {
                if (n.getRetryCount() >= maxRetries) {
                    n.setStatus("DEAD_LETTER");
                    n.setNextRetryAt(null);
                    notificationRepository.save(n);
                    log.warn("Notification moved to dead letter: id={}, user={}, retries={}",
                            n.getId(), n.getUser().getUsername(), n.getRetryCount());
                    continue;
                }

                n.setStatus("RETRYING");
                n.setRetryCount(n.getRetryCount() + 1);
                int backoffIndex = Math.min(n.getRetryCount() - 1, BACKOFF_MS.length - 1);
                long jitter = ThreadLocalRandom.current().nextLong(0, BACKOFF_MS[backoffIndex] / 10);
                n.setNextRetryAt(LocalDateTime.now().plus(Duration.ofMillis(BACKOFF_MS[backoffIndex] + jitter)));
                notificationRepository.save(n);

                liveEventService.publish("notif.changed", "");
                n.setStatus("SENT");
                n.setNextRetryAt(null);
                notificationRepository.save(n);
                metrics.incrementRetried();
                log.debug("Retry succeeded: id={}, attempt={}", n.getId(), n.getRetryCount());
            } catch (Exception e) {
                log.error("Retry failed: id={}, attempt={}, error={}", n.getId(), n.getRetryCount(), e.getMessage());
                n.setStatus("FAILED");
                notificationRepository.save(n);
            }
        }
    }
}
