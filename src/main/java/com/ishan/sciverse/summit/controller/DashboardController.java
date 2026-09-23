package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.data.Presentation;
import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.repository.PresentationRepository;
import com.ishan.sciverse.summit.service.LiveEventService;
import com.ishan.sciverse.summit.service.SessionService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class DashboardController {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private PresentationRepository presentationRepository;

    @Autowired
    private LiveEventService liveEventService;

    @Autowired
    private com.ishan.sciverse.summit.service.AuditService auditService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        try {
            model.addAttribute("newSession", new Session());
            sessionService.getActiveSession().ifPresent(session ->
                model.addAttribute("activeSession", session)
            );
            model.addAttribute("activeSessions", sessionService.getActiveSessions());
        } catch (Exception ex) {
            // DB not available — still render the page so the browser gets a complete response
            model.addAttribute("newSession", new Session());
            model.addAttribute("activeSessions", java.util.Collections.emptyList());
        }
        return "dashboard";
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("sessions", sessionService.getUserSessions());
        return "history";
    }

    @PostMapping("/session/create")
    public String createSession(@ModelAttribute Session session, RedirectAttributes ra) {
        if (sessionService.countActiveSessions() >= 3) {
            ra.addFlashAttribute("sessionLimitError",
                "You can have a maximum of 3 active sessions at once. Please end an active session before creating a new one.");
            return "redirect:/dashboard";
        }
        sessionService.createSession(session);
        liveEventService.publish("session.changed", "");
        return "redirect:/";
    }

    @PostMapping("/session/end")
    public String endSession(@RequestParam Long sessionId,
                             @RequestParam(required = false, defaultValue = "") String ebReview) {
        sessionService.endSession(sessionId, ebReview);
        liveEventService.publish("session.changed", "");
        return "redirect:/dashboard";
    }

    @GetMapping("/history/export-csv")
    public void exportHistoryCsv(
            @RequestParam(required = false) Long sessionId,
            HttpServletResponse response) throws IOException {
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

        // Determine which sessions to export
        List<Session> sessions;
        String filename;
        if (sessionId != null) {
            // Single-session export
            Session single = sessionService.getSessionById(sessionId).orElse(null);
            if (single == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Session not found");
                return;
            }
            sessions = List.of(single);
            // Sanitise the session name for use in a filename
            String safeName = single.getName().replaceAll("[^a-zA-Z0-9_\\-]", "_");
            filename = "session_" + safeName + ".csv";
        } else {
            sessions = sessionService.getUserSessions();
            filename = "session_history.csv";
        }

        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");

        PrintWriter writer = response.getWriter();
        // BOM for Excel UTF-8 compatibility
        writer.write('\uFEFF');

        // Readable, self-documenting CSV layout: session info up top, delegate table below
        final String SEP = "=====================";

        for (Session session : sessions) {
            String sessionName = session.getName();
            String committee   = session.getCommittee();
            String topic       = session.getTopic();
            String createdAt   = session.getCreatedAt() != null ? session.getCreatedAt().format(dtf) : "";
            String status      = Boolean.TRUE.equals(session.getActive()) ? "Active" : "Ended";
            String ebReview    = session.getEbReview();
            String notes       = session.getNotes();
            List<Presentation> delegates = presentationRepository.findBySessionOrderByIdAsc(session);

            writer.println("SESSION REPORT");
            writer.println(SEP);
            writer.println("Session Name," + csvField(sessionName));
            writer.println("Committee," + csvField(committee));
            writer.println("Topic," + csvField(topic));
            writer.println("Date Created," + createdAt);
            writer.println("Status," + status);
            writer.println("Total Participants (Strength)," + session.getStrength());
            writer.println("Number of Delegates Present," + delegates.size());
            writer.println();

            writer.println("SESSION NOTES");
            writer.println(SEP);
            writer.println(csvField(notes != null && !notes.isBlank() ? notes : "No notes recorded for this session."));
            writer.println();

            writer.println("EXECUTIVE BOARD REVIEW");
            writer.println(SEP);
            writer.println(csvField(ebReview != null && !ebReview.isBlank() ? ebReview : "No EB review recorded for this session."));
            writer.println();

            writer.println("DELEGATE PERFORMANCE");
            writer.println(SEP);
            writer.println(
                "Delegate Name,Presenting,Voting," +
                "Sci Knowledge (25),Representation Accuracy (15),Public Speaking (15)," +
                "Participation (15),Resolution Drafting (15),Collaboration (10)," +
                "Leadership Matrix (5),Total Score (100),Times Spoken,Total Speaking Time (sec)"
            );

            if (delegates.isEmpty()) {
                writer.println("\"No delegates recorded for this session.\"");
            } else {
                for (Presentation d : delegates) {
                    writer.println(
                        csvField(d.getName()) + "," +
                        (d.isPresenting() ? "Yes" : "No") + "," +
                        (d.isVoting() ? "Yes" : "No") + "," +
                        d.getSciKnowledge() + "," +
                        d.getRepresentationAccuracy() + "," +
                        d.getPublicSpeaking() + "," +
                        d.getParticipation() + "," +
                        d.getResolutionDrafting() + "," +
                        d.getCollaboration() + "," +
                        d.getLeadershipMatrix() + "," +
                        d.getMatrixScore() + "," +
                        d.getTimesSpoken() + "," +
                        d.getTotalSpeakingTime()
                    );
                }
            }
            writer.println();
        }
        writer.flush();
        auditService.log("SESSION_CSV_EXPORTED", "SESSION", sessionId,
                sessionId != null ? "Exported CSV for session " + sessionId + "."
                        : "Exported CSV for all sessions (" + sessions.size() + ").");
    }

    @GetMapping("/history/audit")
    @ResponseBody
    public Map<String, Object> auditPreview(@RequestParam Long sessionId) {
        // Same strict ownership as the CSV export.
        var sessionOpt = sessionService.getOwnedSession(sessionId);
        Map<String, Object> out = new LinkedHashMap<>();
        if (sessionOpt.isEmpty()) {
            out.put("success", false);
            out.put("message", "Session not found.");
            return out;
        }
        var fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        var items = new ArrayList<Map<String, Object>>();
        for (var e : auditService.search(sessionId, null, null, null)) {
            var o = new LinkedHashMap<String, Object>();
            o.put("time", e.getCreatedAt() != null ? e.getCreatedAt().format(fmt) : "-");
            o.put("actor", e.getActorUsername());
            o.put("role", e.getActorRole());
            o.put("action", e.getAction());
            o.put("details", e.getDetails());
            items.add(o);
        }
        out.put("success", true);
        out.put("sessionName", sessionOpt.get().getName());
        out.put("items", items);
        return out;
    }

    @GetMapping("/history/audit-csv")
    public void exportAuditCsv(
            @RequestParam Long sessionId,
            HttpServletResponse response) throws IOException {
        // Strict ownership: a chair can only pull the audit of their own session.
        var sessionOpt = sessionService.getOwnedSession(sessionId);
        if (sessionOpt.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Session not found");
            return;
        }
        response.setContentType("text/csv; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"audit_session_" + sessionId + ".csv\"");
        PrintWriter auditWriter = response.getWriter();
        auditWriter.write('﻿');
        auditWriter.write(auditService.toCsv(sessionId));
        auditWriter.flush();
    }

    /** Wraps a string in double-quotes and escapes embedded double-quotes for CSV. */
    private String csvField(String value) {
        if (value == null || value.isBlank()) return "\"\"";
        return "\"" + value.replace("\"", "\"\"").replace("\n", " ").replace("\r", "") + "\"";
    }

    @PostMapping("/session/notes")
    public String saveNotes(@org.springframework.web.bind.annotation.RequestParam Long sessionId, 
                          @org.springframework.web.bind.annotation.RequestParam String notes,
                          jakarta.servlet.http.HttpServletRequest request) {
        System.out.println("DEBUG: Saving notes for session ID: " + sessionId);
        System.out.println("DEBUG: Notes content: " + notes);
        
        if (sessionId != null && sessionId > 0) {
            sessionService.saveNotes(sessionId, notes);
            liveEventService.publish("stats.changed", "");
            System.out.println("DEBUG: Notes saved successfully");
        } else {
            System.err.println("ERROR: Invalid session ID: " + sessionId);
        }
        
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/notes");
    }

    @PostMapping("/api/session/notes/delete")
    @org.springframework.web.bind.annotation.ResponseBody
    public String deleteNotes(@org.springframework.web.bind.annotation.RequestParam Long sessionId) {
        sessionService.saveNotes(sessionId, null);
        liveEventService.publish("stats.changed", "");
        return "deleted";
    }

    @GetMapping("/api/session/notes/get")
    @org.springframework.web.bind.annotation.ResponseBody
    public String getNotes(@org.springframework.web.bind.annotation.RequestParam Long sessionId) {
        return sessionService.getSessionById(sessionId)
                .map(session -> session.getNotes() != null ? session.getNotes() : "")
                .orElse("");
    }

    @PostMapping("/api/sessions/delete-all")
    @org.springframework.web.bind.annotation.ResponseBody
    public String deleteAllSessions() {
        sessionService.deleteAllSessions();
        liveEventService.publish("session.changed", "");
        return "deleted";
    }

    @PostMapping("/api/session/delete")
    @org.springframework.web.bind.annotation.ResponseBody
    public String deleteSession(@org.springframework.web.bind.annotation.RequestParam Long sessionId) {
        sessionService.deleteSession(sessionId);
        liveEventService.publish("session.changed", "");
        return "deleted";
    }
}
