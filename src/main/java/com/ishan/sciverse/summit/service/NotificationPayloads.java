package com.ishan.sciverse.summit.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.sciverse.summit.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds the JSON payload carried by the {@code notif.changed} SSE event.
 *
 * <p>Shared rather than private to {@link NotificationService} because the
 * outbox re-publishes a notification on retry and must emit exactly the same
 * shape the browser client parses.
 */
@Component
public class NotificationPayloads {

    private static final Logger log = LoggerFactory.getLogger(NotificationPayloads.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public String build(Notification n) {
        if (n == null) {
            return "";
        }
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
}
