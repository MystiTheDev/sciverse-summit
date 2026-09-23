package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.entity.Notification;
import com.ishan.sciverse.summit.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
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
 * {@code GET /api/notifications} every few seconds.
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
    public String markRead(@RequestParam Long id, Principal principal) {
        if (principal != null) {
            notificationService.markRead(id, principal.getName());
        }
        return "ok";
    }

    @PostMapping("/api/notifications/read-all")
    public String markAllRead(Principal principal) {
        if (principal != null) {
            notificationService.markAllRead(principal.getName());
        }
        return "ok";
    }

    @PostMapping("/api/notifications/clear-all")
    public String clearAll(Principal principal) {
        if (principal != null) {
            notificationService.clearAll(principal.getName());
        }
        return "ok";
    }

    @PostMapping("/api/notifications/delete")
    public String deleteOne(@RequestParam Long id, Principal principal) {
        if (principal != null) {
            notificationService.deleteOne(id, principal.getName());
        }
        return "ok";
    }
}
