package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.entity.DelegateMembership;
import com.ishan.sciverse.summit.entity.Notification;
import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.entity.User;
import com.ishan.sciverse.summit.repository.DelegateMembershipRepository;
import com.ishan.sciverse.summit.repository.NotificationRepository;
import com.ishan.sciverse.summit.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int MAX_LINK_LENGTH = 500;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "GENERAL", "VOTING", "MOTION", "RESOLUTION",
            "DELEGATE_JOINED", "DELEGATE_LEFT", "SESSION_ENDED",
            "SPEAKER"
    );

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DelegateMembershipRepository membershipRepository;

    @Autowired
    private LiveEventService liveEventService;

    @Autowired
    private NotificationOutboxService outboxService;

    @Autowired
    private NotificationMetrics metrics;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String notificationPayload(Notification n) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", n.getId());
        map.put("userId", n.getUser() != null ? n.getUser().getUsername() : "");
        map.put("title", n.getTitle() != null ? n.getTitle() : "");
        map.put("message", n.getMessage() != null ? n.getMessage() : "");
        map.put("type", n.getType() != null ? n.getType() : "GENERAL");
        map.put("link", n.getLink() != null ? n.getLink() : "");
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            log.warn("Failed to serialize notification payload: {}", e.getMessage());
            return "";
        }
    }

    public Notification notifyUserQuietly(User user, String title, String message, String type, String link) {
        try {
            title = validateAndSanitizeTitle(title);
            message = validateAndSanitizeMessage(message);
            type = validateType(type);
            link = validateAndSanitizeLink(link);

            Notification n = new Notification();
            n.setUser(user);
            n.setTitle(title);
            n.setMessage(message);
            n.setType(type);
            n.setLink(link);
            n.setRead(false);
            n.setStatus("PENDING");
            n.setRetryCount(0);
            Notification saved = notificationRepository.save(n);

            log.info("Notification created (quiet): id={}, user={}, type={}", saved.getId(), user.getUsername(), type);
            metrics.incrementCreated();
            return saved;
        } catch (Exception e) {
            log.error("Failed to create quiet notification for user={}, type={}: {}", user.getUsername(), type, e.getMessage());
            metrics.incrementFailed();
            outboxService.enqueueFailedNotification(user, title, message, type, link);
            throw e;
        }
    }

    public Notification notifyUser(User user, String title, String message, String type, String link) {
        try {
            title = validateAndSanitizeTitle(title);
            message = validateAndSanitizeMessage(message);
            type = validateType(type);
            link = validateAndSanitizeLink(link);

            Notification n = new Notification();
            n.setUser(user);
            n.setTitle(title);
            n.setMessage(message);
            n.setType(type);
            n.setLink(link);
            n.setRead(false);
            n.setStatus("PENDING");
            n.setRetryCount(0);
            Notification saved = notificationRepository.save(n);

            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        liveEventService.publish("notif.changed", notificationPayload(saved));
                    }
                });
            } else {
                liveEventService.publish("notif.changed", notificationPayload(saved));
            }

            log.info("Notification created: id={}, user={}, type={}, sse_fired={}", saved.getId(), user.getUsername(), type, TransactionSynchronizationManager.isSynchronizationActive() ? "afterCommit" : "immediate");
            metrics.incrementCreated();
            return saved;
        } catch (Exception e) {
            log.error("Failed to create notification for user={}, type={}: {}", user.getUsername(), type, e.getMessage());
            metrics.incrementFailed();
            outboxService.enqueueFailedNotification(user, title, message, type, link);
            throw e;
        }
    }

    @Transactional
    public int notifySessionDelegates(Session session, String title, String message, String type, String link) {
        List<DelegateMembership> memberships = membershipRepository.findBySession(session);
        if (memberships.isEmpty()) {
            return 0;
        }

        title = validateAndSanitizeTitle(title);
        message = validateAndSanitizeMessage(message);
        type = validateType(type);
        link = validateAndSanitizeLink(link);

        List<Notification> notifications = new ArrayList<>();
        for (DelegateMembership m : memberships) {
            Notification n = new Notification();
            n.setUser(m.getUser());
            n.setTitle(title);
            n.setMessage(message);
            n.setType(type);
            n.setLink(link);
            n.setRead(false);
            n.setStatus("PENDING");
            n.setRetryCount(0);
            notifications.add(n);
        }

        List<Notification> saved = notificationRepository.saveAll(notifications);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    liveEventService.publish("notif.changed", saved.isEmpty() ? "" : notificationPayload(saved.get(0)));
                }
            });
        } else {
            liveEventService.publish("notif.changed", saved.isEmpty() ? "" : notificationPayload(saved.get(0)));
        }

        log.info("Batch notification created: count={}, session={}, type={}", saved.size(), session.getId(), type);
        for (int i = 0; i < saved.size(); i++) {
            metrics.incrementCreated();
        }
        return saved.size();
    }

    public List<Notification> listForUser(String username) {
        return userRepository.findByUsername(username)
                .map(notificationRepository::findByUserOrderByCreatedAtDesc)
                .orElse(List.of());
    }

    public long unreadCount(String username) {
        return userRepository.findByUsername(username)
                .map(u -> notificationRepository.countByUserAndReadFalse(u))
                .orElse(0L);
    }

    public Optional<Notification> getById(Long id) {
        return notificationRepository.findById(id);
    }

    public void markRead(Long id, String username) {
        notificationRepository.findById(id).ifPresent(n -> {
            if (n.getUser() != null && n.getUser().getUsername().equalsIgnoreCase(username)) {
                n.setRead(true);
                notificationRepository.save(n);
            }
        });
    }

    public void markAllRead(String username) {
        userRepository.findByUsername(username).ifPresent(u ->
                notificationRepository.findByUserOrderByCreatedAtDesc(u)
                        .forEach(n -> {
                            n.setRead(true);
                            notificationRepository.save(n);
                        }));
    }

    @Transactional
    public long clearAll(String username) {
        return userRepository.findByUsername(username)
                .map(u -> notificationRepository.deleteByUser(u))
                .orElse(0L);
    }

    @Transactional
    public boolean deleteOne(Long id, String username) {
        Notification n = notificationRepository.findById(id).orElse(null);
        if (n == null || n.getUser() == null || !n.getUser().getUsername().equalsIgnoreCase(username)) {
            return false;
        }
        notificationRepository.delete(n);
        return true;
    }

    private String validateAndSanitizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return "Notification";
        }
        String trimmed = title.trim();
        if (trimmed.length() > MAX_TITLE_LENGTH) {
            trimmed = trimmed.substring(0, MAX_TITLE_LENGTH);
        }
        return trimmed;
    }

    private String validateAndSanitizeMessage(String message) {
        if (message == null) {
            return "";
        }
        String trimmed = message.trim();
        if (trimmed.length() > MAX_MESSAGE_LENGTH) {
            trimmed = trimmed.substring(0, MAX_MESSAGE_LENGTH);
        }
        return trimmed;
    }

    private String validateType(String type) {
        if (type == null || type.isBlank()) {
            return "GENERAL";
        }
        String upper = type.trim().toUpperCase();
        if (!ALLOWED_TYPES.contains(upper)) {
            log.warn("Unknown notification type '{}', defaulting to GENERAL", type);
            return "GENERAL";
        }
        return upper;
    }

    private String validateAndSanitizeLink(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        String trimmed = link.trim();
        if (trimmed.length() > MAX_LINK_LENGTH) {
            trimmed = trimmed.substring(0, MAX_LINK_LENGTH);
        }
        if (!trimmed.startsWith("/")) {
            log.warn("Rejected non-internal link: {}", trimmed);
            return null;
        }
        return trimmed;
    }
}
