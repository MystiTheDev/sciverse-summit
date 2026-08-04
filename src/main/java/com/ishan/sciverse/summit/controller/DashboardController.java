package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.data.Presentation;
import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.repository.PresentationRepository;
import com.ishan.sciverse.summit.service.SessionService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class DashboardController {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private PresentationRepository presentationRepository;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        try {
            model.addAttribute("newSession", new Session());
            sessionService.getActiveSession().ifPresent(session ->
                model.addAttribute("activeSession", session)
            );
        } catch (Exception ex) {
            // DB not available — still render the page so the browser gets a complete response
            model.addAttribute("newSession", new Session());
        }
        return "dashboard";
    }

    @GetMapping("/history")
    public String history(Model model) {
        model.addAttribute("sessions", sessionService.getUserSessions());
        return "history";
    }

    @PostMapping("/session/create")
    public String createSession(@ModelAttribute Session session) {
        sessionService.createSession(session);
        return "redirect:/";
    }

    @PostMapping("/session/end")
    public String endSession(@RequestParam Long sessionId,
                             @RequestParam(required = false, defaultValue = "") String ebReview) {
        sessionService.endSession(sessionId, ebReview);
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

        // Header
        writer.println(
            "Session Name,Committee,Topic,Strength,Created At,Status,EB Review,Notes," +
            "Delegate Name,Presenting,Voting," +
            "Sci Knowledge (25),Representation Accuracy (15),Public Speaking (15)," +
            "Participation (15),Resolution Drafting (15),Collaboration (10)," +
            "Leadership Matrix (5),Total Score (100),Times Spoken,Total Speaking Time (sec)"
        );

        for (Session session : sessions) {
            String sessionName = csvField(session.getName());
            String committee   = csvField(session.getCommittee());
            String topic       = csvField(session.getTopic());
            String createdAt   = session.getCreatedAt() != null ? session.getCreatedAt().format(dtf) : "";
            String status      = Boolean.TRUE.equals(session.getActive()) ? "Active" : "Ended";
            String ebReview    = csvField(session.getEbReview());
            String notes       = csvField(session.getNotes());
            String sessionCols = sessionName + "," + committee + "," + topic + "," +
                                 session.getStrength() + ",\"" + createdAt + "\"," + status + "," +
                                 ebReview + "," + notes;

            List<Presentation> delegates = presentationRepository.findBySessionOrderByIdAsc(session);
            if (delegates.isEmpty()) {
                writer.println(sessionCols + ",,,,,,,,,,,");
            } else {
                for (Presentation d : delegates) {
                    writer.println(
                        sessionCols + "," +
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
        }
        writer.flush();
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
        return "deleted";
    }

    @PostMapping("/api/session/delete")
    @org.springframework.web.bind.annotation.ResponseBody
    public String deleteSession(@org.springframework.web.bind.annotation.RequestParam Long sessionId) {
        sessionService.deleteSession(sessionId);
        return "deleted";
    }
}
