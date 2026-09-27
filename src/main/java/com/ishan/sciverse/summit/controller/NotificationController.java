package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.entity.Notification;
import com.ishan.sciverse.summit.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints backing the header notification bell. The bell polls
 * {@code GET /api/notifications} every few seconds and is nudged sooner by the
 * {@code notif.changed} SSE event.
 *
 * <p>Mutations report honestly: an id that does not exist, or belongs to
 * someone else, returns 404 rather than a cheerful {@code "ok"}. Previously
 * every one of these returned {@code "ok"} even when nothing had happened, so
 * a client could not tell "marked as read" from "not yours".
 */
@RestController
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    @GetMapping("/api/notifications")
    public Map<String, Object> notifications(Principal principal) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (principal == null) {
            out.put("unread", 0);
            out.put("items", new ArrayList<>());
            return out;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Notification n : notificationService.listForUser(principal.getName())) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", n.getId());
            o.put("title", n.getTitle());
            o.put("message", n.getMessage());
            o.put("type", n.getType());
            o.put("link", n.getLink());
            o.put("read", n.isRead());
            o.put("time", n.getCreatedAt() != null ? n.getCreatedAt().format(TIME) : "");
            items.add(o);
        }
        out.put("unread", notificationService.unreadCount(principal.getName()));
        out.put("items", items);
        return out;
    }

    @PostMapping("/api/notifications/read")
    public ResponseEntity<String> markRead(@RequestParam Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthenticated");
        }
        return notificationService.markRead(id, principal.getName())
                ? ResponseEntity.ok("ok")
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body("not found");
    }

    @PostMapping("/api/notifications/clear-all")
    public ResponseEntity<String> clearAll(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthenticated");
        }
        notificationService.clearAll(principal.getName());
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/api/notifications/delete")
    public ResponseEntity<String> deleteOne(@RequestParam Long id, Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("unauthenticated");
        }
        return notificationService.deleteOne(id, principal.getName())
                ? ResponseEntity.ok("ok")
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body("not found");
    }
}
