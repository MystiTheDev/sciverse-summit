package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.service.AuditService;
import com.ishan.sciverse.summit.service.SessionService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/** Chair/admin-only audit trail viewer + CSV export. */
@Controller
public class AuditController {

    @Autowired
    private AuditService auditService;

    @Autowired
    private SessionService sessionService;

    /**
     * Session-locked viewer: only the chair's ACTIVE session (falling back to
     * the latest) is ever shown or exported here. Other sessions' logs are
     * reachable solely through their own history-row download, never by
     * switching context inside this page.
     */
    @GetMapping("/admin/audit")
    public String auditPage(@RequestParam(required = false) String action,
                            @RequestParam(required = false) String actor,
                            @RequestParam(required = false) String q,
                            Model model) {
        var active = sessionService.getActiveSession()
                .or(() -> sessionService.getLatestSession());
        model.addAttribute("activeSession", active.orElse(null));
        model.addAttribute("entries", active
                .map(s -> auditService.search(s.getId(), action, actor, q))
                .orElse(List.of()));
        model.addAttribute("actions", auditService.distinctActions());
        model.addAttribute("action", action);
        model.addAttribute("actor", actor);
        model.addAttribute("q", q);
        return "admin-audit";
    }

    @GetMapping("/admin/audit/export-csv")
    public void exportCsv(@RequestParam(required = false) String action,
                          @RequestParam(required = false) String actor,
                          @RequestParam(required = false) String q,
                          HttpServletResponse response) throws IOException {
        var active = sessionService.getActiveSession()
                .or(() -> sessionService.getLatestSession());
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"audit-log.csv\"");
        PrintWriter writer = response.getWriter();
        writer.write("﻿");
        writer.write(active.map(s -> auditService.toCsv(s.getId())).orElse(""));
        writer.flush();
    }
}
