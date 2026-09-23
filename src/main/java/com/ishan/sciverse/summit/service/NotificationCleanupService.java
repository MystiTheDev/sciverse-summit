package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.entity.User;
import com.ishan.sciverse.summit.repository.NotificationRepository;
import com.ishan.sciverse.summit.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class NotificationCleanupService {

    private static final Logger log = LoggerFactory.getLogger(NotificationCleanupService.class);

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationMetrics metrics;

    @Value("${notification.ttl-days:30}")
    private int ttlDays;

    @Value("${notification.max-per-user:200}")
    private int maxPerUser;

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupOldNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(ttlDays);
        long deleted = notificationRepository.deleteByCreatedAtBefore(cutoff);
        if (deleted > 0) {
            log.info("Cleaned up {} notifications older than {} days", deleted, ttlDays);
            for (int i = 0; i < deleted; i++) {
                metrics.incrementCleaned();
            }
        }
    }

    @Scheduled(cron = "0 30 3 * * ?")
    @Transactional
    public void enforceMaxPerUser() {
        List<User> allUsers = userRepository.findAll();
        int trimmed = 0;
        for (User user : allUsers) {
            long count = notificationRepository.countByUserAndReadFalse(user);
            long total = notificationRepository.findByUserOrderByCreatedAtDesc(user).size();
            if (total > maxPerUser) {
                List<com.ishan.sciverse.summit.entity.Notification> oldest =
                        notificationRepository.findOldestForUser(user, PageRequest.of(0, (int)(total - maxPerUser)));
                notificationRepository.deleteAll(oldest);
                trimmed += oldest.size();
            }
        }
        if (trimmed > 0) {
            log.info("Trimmed {} notifications to enforce max {} per user", trimmed, maxPerUser);
        }
    }
}
