package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class DashboardController {

    @Autowired
    private SessionService sessionService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("newSession", new Session());
        sessionService.getActiveSession().ifPresent(session -> 
            model.addAttribute("activeSession", session)
        );
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
    public String endSession(@org.springframework.web.bind.annotation.RequestParam Long sessionId) {
        sessionService.endSession(sessionId);
        return "redirect:/dashboard";
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
