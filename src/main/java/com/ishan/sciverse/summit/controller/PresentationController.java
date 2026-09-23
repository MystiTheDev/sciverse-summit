package com.ishan.sciverse.summit.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.ishan.sciverse.summit.data.Presentation;
import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.repository.PresentationRepository;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.Optional;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.web.multipart.MultipartFile;

@Controller
public class PresentationController {

    @Autowired
    private PresentationRepository presentationRepository;
    
    @Autowired
    private com.ishan.sciverse.summit.service.SessionService sessionService;

    @Autowired
    private com.ishan.sciverse.summit.service.LiveEventService liveEventService;

    @Autowired
    private com.ishan.sciverse.summit.service.AuditService auditService;

    // READ: Display list of all presentations
    @GetMapping("/")
    public String listPresentations(@RequestParam(required = false) Long sessionId, Model model) {
        // Show delegates for the selected session (or the current/latest session by default)
        var sessionOpt = sessionId != null
                ? sessionService.getOwnedSession(sessionId)
                        .or(() -> sessionService.getActiveSession().or(() -> sessionService.getLatestSession()))
                : sessionService.getActiveSession().or(() -> sessionService.getLatestSession());

        if (sessionOpt.isPresent()) {
            var session = sessionOpt.get();
            var delegates = presentationRepository.findBySessionOrderByIdAsc(session);
            model.addAttribute("presentations", delegates);
            model.addAttribute("currentSession", session);
            model.addAttribute("delegateCount", delegates.size());
        } else {
            model.addAttribute("presentations", java.util.Collections.emptyList());
        }
        return "index"; // Corresponds to src/main/resources/templates/index.html
    }

    // CREATE: Show form to add a new presentation
    @GetMapping("/add")
    public String showAddForm(Presentation presentation) {
        return "add-edit"; // Corresponds to src/main/resources/templates/add-edit.html
    }
    
 // ⬇️ NEW METHOD: Show the Edit Form ⬇️
    @GetMapping("/edit/{id}")
    public String showUpdateForm(@PathVariable("id") long id, Model model) {
        // Fetch the record by ID
        Presentation presentation = presentationRepository.findById(id)
          .orElseThrow(() -> new IllegalArgumentException("Invalid presentation Id:" + id));
        
        // Add the retrieved object to the model so Thymeleaf can pre-fill the form
        model.addAttribute("presentation", presentation);
        
        // We reuse the same form template used for adding new records
        return "add-edit"; 
    }
    
   // CREATE: Show form to add a new presentation
    @GetMapping("/unmod")
    public String showUnmodForm(Presentation presentation, Model model) {
        sessionService.getActiveSession().or(() -> sessionService.getLatestSession())
                .ifPresent(s -> model.addAttribute("currentSession", s));
        return "unmod"; // Corresponds to src/main/resources/templates/unmod.html
    }

    // CREATE/UPDATE: Handle form submission to save/update a presentation
    @PostMapping("/save")
    public String savePresentation(Presentation presentation) {
        boolean isNew = presentation.getId() == null;
        // Link to current session if it's a new delegate
        if (presentation.getId() == null) {
            var sessionOpt = sessionService.getActiveSession()
                    .or(() -> sessionService.getLatestSession());
            if (sessionOpt.isPresent()) {
                var session = sessionOpt.get();
                // Server-side delegate limit enforcement
                if (session.getStrength() > 0) {
                    int currentCount = presentationRepository.findBySessionOrderByIdAsc(session).size();
                    if (currentCount >= session.getStrength()) {
                        return "redirect:/add"; // Limit reached — refuse silently (UI already blocks)
                    }
                }
                presentation.setSession(session);
            }
        } else {
            // For existing delegates, preserve the session and stats
            Presentation existing = presentationRepository.findById(presentation.getId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid presentation Id:" + presentation.getId()));
            
            // Preserve session relationship
            presentation.setSession(existing.getSession());
            
            // Preserve stats that shouldn't be modified through this form
            presentation.setTimesSpoken(existing.getTimesSpoken());
            presentation.setTotalSpeakingTime(existing.getTotalSpeakingTime());
            presentation.setMotionProposals(existing.getMotionProposals());
            presentation.setAmendmentProposals(existing.getAmendmentProposals());
            
            // Preserve evaluation scores
            presentation.setSciKnowledge(existing.getSciKnowledge());
            presentation.setRepresentationAccuracy(existing.getRepresentationAccuracy());
            presentation.setPublicSpeaking(existing.getPublicSpeaking());
            presentation.setParticipation(existing.getParticipation());
            presentation.setResolutionDrafting(existing.getResolutionDrafting());
            presentation.setCollaboration(existing.getCollaboration());
            presentation.setLeadershipMatrix(existing.getLeadershipMatrix());
            
            // Preserve chair feedback written for this delegate
            presentation.setDelegateNotes(existing.getDelegateNotes());
        }
        presentationRepository.save(presentation);
        Long sid = presentation.getSession() != null ? presentation.getSession().getId() : null;
        auditService.log(isNew ? "DELEGATE_ADDED" : "DELEGATE_EDITED", "DELEGATE",
                presentation.getId(), sid,
                (isNew ? "Added delegate \"" : "Edited delegate \"") + presentation.getName() + "\".");
        liveEventService.publish("delegate.changed", "");
        return "redirect:/"; // Redirect back to the list view
    }

    // DELETE: Delete a presentation and redirect back to list
    @GetMapping("/delete/{id}")
    public String deletePresentation(@PathVariable("id") long id) {
        Presentation presentation = presentationRepository.findById(id)
          .orElseThrow(() -> new IllegalArgumentException("Invalid presentation Id:" + id));
        
        Long delSid = presentation.getSession() != null ? presentation.getSession().getId() : null;
        auditService.log("DELEGATE_DELETED", "DELEGATE", id, delSid,
                "Deleted delegate \"" + presentation.getName() + "\".");
        presentationRepository.delete(presentation);
        liveEventService.publish("delegate.changed", "");
        return "redirect:/";
    }
    
 // ⬇️ NEW SPEAKERS' LIST PAGE HANDLER ⬇️
    @GetMapping("/speakers")
    public String showSpeakersList(Model model) {
        // Get current session (active first, otherwise the latest)
        var sessionOpt = sessionService.getActiveSession()
                .or(() -> sessionService.getLatestSession());

        // Fetch ONLY delegates linked to the current session
        List<Presentation> allDelegates;
        if (sessionOpt.isPresent()) {
            allDelegates = presentationRepository
                    .findBySessionOrderByIdAsc(sessionOpt.get());
        } else {
            allDelegates = java.util.Collections.emptyList();
        }

        List<String> presenterNames = allDelegates.stream()
                .map(Presentation::getName)
                .distinct()
                .collect(Collectors.toList());

        // Block access when there are no delegates
        if (presenterNames.isEmpty()) {
            return "redirect:/?noDelegates=speakers";
        }

        // Convert lists to JSON strings for use in JavaScript
        String namesJson = presenterNames.stream()
            .map(name -> "'" + name.replace("'", "\\'") + "'")
            .collect(Collectors.joining(","));

        String presentingNamesJson = allDelegates.stream()
            .filter(Presentation::isPresenting)
            .map(p -> "'" + p.getName().replace("'", "\\'") + "'")
            .collect(Collectors.joining(","));

        String votingNamesJson = allDelegates.stream()
            .filter(Presentation::isVoting)
            .map(p -> "'" + p.getName().replace("'", "\\'") + "'")
            .collect(Collectors.joining(","));

        model.addAttribute("presenterNamesJson", namesJson);
        model.addAttribute("presentingNamesJson", presentingNamesJson);
        model.addAttribute("votingNamesJson", votingNamesJson);
        sessionService.getActiveSession().or(() -> sessionService.getLatestSession())
                .ifPresent(s -> model.addAttribute("currentSession", s));
        return "speakers";
    }
    
    @GetMapping("/motions")
    public String showMotions(Model model) {
        // Get current session (active first, otherwise the latest)
        var sessionOpt = sessionService.getActiveSession()
                .or(() -> sessionService.getLatestSession());

        // Fetch ONLY delegates linked to the current session for autocompletion
        List<String> presenterNames;
        if (sessionOpt.isPresent()) {
            presenterNames = presentationRepository
                    .findBySessionOrderByIdAsc(sessionOpt.get())
                    .stream()
                    .map(Presentation::getName)
                    .distinct()
                    .collect(Collectors.toList());
        } else {
            presenterNames = java.util.Collections.emptyList();
        }

        // Block access when there are no delegates
        if (presenterNames.isEmpty()) {
            return "redirect:/?noDelegates=motions";
        }

        // Convert list to a JSON string for easy use in JavaScript
        String namesJson = presenterNames.stream()
            .map(name -> "'" + name.replace("'", "\\'") + "'") // Simple escaping
            .collect(Collectors.joining(","));

        model.addAttribute("presenterNamesJson", namesJson);
        model.addAttribute("delegateList", presenterNames);
        return "motions";
    }

    // ⬇️ RESOLUTION PAGE HANDLER ⬇️
    @GetMapping("/resolution")
    public String showResolution(Model model) {
        return "resolution";
    }

    // ⬇️ STATS PAGE HANDLER ⬇️
    @GetMapping("/stats")
    public String showStats(Model model) {
        // Get current session
        var sessionOpt = sessionService.getActiveSession()
                .or(() -> sessionService.getLatestSession());
        
        if (sessionOpt.isPresent()) {
            com.ishan.sciverse.summit.entity.Session session = sessionOpt.get();
            // Only get delegates for THIS session
            var delegates = presentationRepository.findBySessionOrderByIdAsc(session);
            model.addAttribute("delegates", delegates);

            // Aggregate stats for the summary tiles
            int totalSpoken = 0;
            long totalSeconds = 0;
            int totalMotions = 0;
            int totalAmendments = 0;
            com.ishan.sciverse.summit.data.Presentation topDelegate = null;
            int topTimes = -1;
            for (var d : delegates) {
                totalSpoken += d.getTimesSpoken();
                totalSeconds += d.getTotalSpeakingTime();
                totalMotions += d.getMotionProposals();
                totalAmendments += d.getAmendmentProposals();
                if (d.getTimesSpoken() > topTimes) {
                    topTimes = d.getTimesSpoken();
                    topDelegate = d;
                }
            }
            model.addAttribute("totalSpoken", totalSpoken);
            model.addAttribute("totalSpeakingSeconds", totalSeconds);
            model.addAttribute("totalMotions", totalMotions);
            model.addAttribute("totalAmendments", totalAmendments);
            model.addAttribute("topDelegate", topDelegate);
        } else {
            model.addAttribute("delegates", java.util.Collections.emptyList());
        }
        return "stats";
    }

    // ⬇️ NOTES PAGE HANDLER ⬇️
    @GetMapping("/notes")
    public String showNotes(Model model) {
        // Get current session
        var sessionOpt = sessionService.getActiveSession()
                .or(() -> sessionService.getLatestSession());
        
        if (sessionOpt.isPresent()) {
            com.ishan.sciverse.summit.entity.Session session = sessionOpt.get();
            // Only get delegates for THIS session
            model.addAttribute("delegates", presentationRepository.findBySessionOrderByIdAsc(session));
        } else {
            model.addAttribute("delegates", java.util.Collections.emptyList());
        }
        return "notes";
    }

    // Resolve a delegate by name within the chair's current session first.
    // Falls back to a global name match for legacy delegates with no session.
    private Optional<Presentation> findDelegateForStats(String name) {
        if (name != null) {
            String trimmed = name.trim();
            if (!trimmed.isEmpty()) {
                var sessionOpt = sessionService.getActiveSession()
                        .or(() -> sessionService.getLatestSession());
                if (sessionOpt.isPresent()) {
                    Optional<Presentation> scoped = presentationRepository
                            .findFirstBySessionAndNameIgnoreCase(sessionOpt.get(), trimmed);
                    if (scoped.isPresent()) {
                        return scoped;
                    }
                }
                return presentationRepository.findAll().stream()
                        .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(trimmed))
                        .findFirst();
            }
        }
        return Optional.empty();
    }

    // API to update speaking stats (Duration only)
    @PostMapping("/api/stats/update-speaking")
    @ResponseBody
    public String updateSpeakingStats(@RequestParam("name") String name, @RequestParam("duration") long duration) {
        Optional<Presentation> delegateOpt = findDelegateForStats(name);

        if (delegateOpt.isPresent()) {
            Presentation delegate = delegateOpt.get();
            // Only update duration here
            delegate.setTotalSpeakingTime(delegate.getTotalSpeakingTime() + duration);
            presentationRepository.save(delegate);
            liveEventService.publish("stats.changed", "");
            return "Success";
        }
        return "Delegate not found";
    }

    // API to increment times spoken
    @PostMapping("/api/stats/increment-spoken")
    @ResponseBody
    public String incrementTimesSpoken(@RequestParam("name") String name) {
        Optional<Presentation> delegateOpt = findDelegateForStats(name);

        if (delegateOpt.isPresent()) {
            Presentation delegate = delegateOpt.get();
            delegate.setTimesSpoken(delegate.getTimesSpoken() + 1);
            presentationRepository.save(delegate);
            liveEventService.publish("stats.changed", "");
            return "Success";
        }
        return "Delegate not found";
    }

    // API to update motion stats
    @PostMapping("/api/stats/update-motion")
    @ResponseBody
    public String updateMotionStats(@RequestParam("name") String name, @RequestParam("type") String type) {
        Optional<Presentation> delegateOpt = findDelegateForStats(name);

        if (delegateOpt.isPresent()) {
            Presentation delegate = delegateOpt.get();
            if ("Introduce amendment".equalsIgnoreCase(type)) {
                delegate.setAmendmentProposals(delegate.getAmendmentProposals() + 1);
            } else {
                delegate.setMotionProposals(delegate.getMotionProposals() + 1);
            }
            presentationRepository.save(delegate);
            liveEventService.publish("stats.changed", "");
            return "Success";
        }
        return "Delegate not found";
    }

    // ⬇️ EVALUATION SCORES UPDATE ⬇️
    @PostMapping("/api/evaluation/update")
    @ResponseBody
    public String updateEvaluation(
            @RequestParam("id") Long id,
            @RequestParam("sciKnowledge") int sciKnowledge,
            @RequestParam("representationAccuracy") int representationAccuracy,
            @RequestParam("publicSpeaking") int publicSpeaking,
            @RequestParam("participation") int participation,
            @RequestParam("resolutionDrafting") int resolutionDrafting,
            @RequestParam("collaboration") int collaboration,
            @RequestParam("leadershipMatrix") int leadershipMatrix,
            @RequestParam(value = "delegateNotes", required = false) String delegateNotes) {
        Optional<Presentation> opt = presentationRepository.findById(id);
        if (opt.isPresent()) {
            Presentation p = opt.get();
            p.setSciKnowledge(sciKnowledge);
            p.setRepresentationAccuracy(representationAccuracy);
            p.setPublicSpeaking(publicSpeaking);
            p.setParticipation(participation);
            p.setResolutionDrafting(resolutionDrafting);
            p.setCollaboration(collaboration);
            p.setLeadershipMatrix(leadershipMatrix);
            if (delegateNotes != null) {
                p.setDelegateNotes(delegateNotes);
            }
            presentationRepository.save(p);
            Long evalSid = p.getSession() != null ? p.getSession().getId() : null;
            auditService.log("EVALUATION_UPDATED", "DELEGATE", id, evalSid,
                    "Updated evaluation for \"" + p.getName() + "\" (matrix total "
                            + p.getMatrixScore() + ").");
            liveEventService.publish("stats.changed", "");
            return "Success";
        }
        return "Delegate not found";
    }

    // ⬇️ DELEGATE NOTES UPDATE (chair feedback shown to the delegate) ⬇️
    @PostMapping("/api/evaluation/notes")
    @ResponseBody
    public String updateDelegateNotes(
            @RequestParam("id") Long id,
            @RequestParam(value = "delegateNotes", required = false) String delegateNotes) {
        Optional<Presentation> opt = presentationRepository.findById(id);
        if (opt.isPresent()) {
            Presentation p = opt.get();
            p.setDelegateNotes(delegateNotes);
            presentationRepository.save(p);
            Long noteSid = p.getSession() != null ? p.getSession().getId() : null;
            auditService.log("DELEGATE_NOTES_UPDATED", "DELEGATE", id, noteSid,
                    "Updated chair notes for \"" + p.getName() + "\".");
            liveEventService.publish("stats.changed", "");
            return "Success";
        }
        return "Delegate not found";
    }

    // ⬇️ IMPORT STUDENTS PAGE ⬇️
    @GetMapping("/import")
    public String showImport(Model model) {
        try {
            List<Session> sessions = sessionService.getUserSessions();
            model.addAttribute("sessions", sessions);
            model.addAttribute("currentSession",
                sessionService.getActiveSession().or(() -> sessionService.getLatestSession()).orElse(null));

            Map<Long, List<String>> namesBySession = new LinkedHashMap<>();
            List<Map<String, Object>> sessionMeta = new ArrayList<>();
            for (Session s : sessions) {
                namesBySession.put(s.getId(),
                    presentationRepository.findBySessionOrderByIdAsc(s).stream()
                        .map(Presentation::getName)
                        .collect(Collectors.toList()));

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", s.getId());
                m.put("name", s.getName());
                m.put("committee", s.getCommittee());
                m.put("active", Boolean.TRUE.equals(s.getActive()));
                m.put("strength", s.getStrength());
                sessionMeta.add(m);
            }
            model.addAttribute("sessionDelegates", namesBySession);
            model.addAttribute("sessionMeta", sessionMeta);
        } catch (Exception ex) {
            model.addAttribute("sessions", java.util.Collections.emptyList());
            model.addAttribute("currentSession", null);
            model.addAttribute("sessionDelegates", java.util.Collections.emptyMap());
            model.addAttribute("sessionMeta", java.util.Collections.emptyList());
        }
        return "import";
    }

    // Import delegate names from a previous session (names only — no presenting/voting/stats)
    @PostMapping("/import/from-session")
    public String importFromSession(@RequestParam Long sourceSessionId) {
        var src = sessionService.getOwnedSession(sourceSessionId);
        var dst = sessionService.getActiveSession().or(() -> sessionService.getLatestSession());
        if (src.isEmpty() || dst.isEmpty()) {
            return "redirect:/import";
        }
        List<String> names = presentationRepository.findBySessionOrderByIdAsc(src.get()).stream()
                .map(Presentation::getName)
                .collect(Collectors.toList());
        int[] result = importNames(dst.get(), names);
        auditService.log("DELEGATES_IMPORTED", "DELEGATE", null, dst.get().getId(),
                "Imported " + result[0] + " delegates from session " + src.get().getId()
                        + " (" + result[1] + " skipped).");
        liveEventService.publish("delegate.changed", "");
        return "redirect:/?sessionId=" + dst.get().getId()
                + "&imported=" + result[0] + "&skipped=" + result[1];
    }

    // Import delegate names from a .txt file (or pasted text)
    @PostMapping("/import/from-file")
    public String importFromFile(@RequestParam(value = "file", required = false) MultipartFile file,
                                 @RequestParam(value = "names", required = false) String pasted) {
        var dst = sessionService.getActiveSession().or(() -> sessionService.getLatestSession());
        if (dst.isEmpty()) {
            return "redirect:/import";
        }

        List<String> names = new ArrayList<>();
        String content = null;
        if (file != null && !file.isEmpty()) {
            try {
                content = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // fall through to pasted text
            }
        }
        if (content == null && pasted != null && !pasted.isBlank()) {
            content = pasted;
        }
        if (content != null && content.startsWith("\uFEFF")) {
            content = content.substring(1);
        }
        if (content != null) {
            for (String line : content.split("\r?\n")) {
                String name = line.trim();
                if (name.isEmpty()) continue;
                int comma = name.indexOf(',');
                if (comma > 0) name = name.substring(0, comma).trim();
                if (!name.isEmpty()) names.add(name);
            }
        }

        int[] result = importNames(dst.get(), names);
        auditService.log("DELEGATES_IMPORTED", "DELEGATE", null, dst.get().getId(),
                "Imported " + result[0] + " delegates from file/pasted text ("
                        + result[1] + " skipped).");
        liveEventService.publish("delegate.changed", "");
        return "redirect:/?sessionId=" + dst.get().getId()
                + "&imported=" + result[0] + "&skipped=" + result[1];
    }

    // ⬇️ BULK DELEGATE ACTIONS (current session) ⬇️
    // Actions: presenting | voting | not-voting | reset-scores | delete-all
    @PostMapping("/api/delegates/bulk")
    @ResponseBody
    public String bulkDelegateAction(@RequestParam String action) {
        var sessionOpt = sessionService.getActiveSession()
                .or(() -> sessionService.getLatestSession());
        if (sessionOpt.isEmpty()) {
            return "No session found";
        }
        List<Presentation> delegates = presentationRepository.findBySessionOrderByIdAsc(sessionOpt.get());
        if (delegates.isEmpty()) {
            return "No delegates found";
        }
        switch (action) {
            case "presenting":
                delegates.forEach(d -> d.setPresenting(true));
                break;
            case "voting":
                delegates.forEach(d -> d.setVoting(true));
                break;
            case "not-voting":
                delegates.forEach(d -> d.setVoting(false));
                break;
            case "reset-scores":
                delegates.forEach(d -> {
                    d.setSciKnowledge(0);
                    d.setRepresentationAccuracy(0);
                    d.setPublicSpeaking(0);
                    d.setParticipation(0);
                    d.setResolutionDrafting(0);
                    d.setCollaboration(0);
                    d.setLeadershipMatrix(0);
                });
                break;
            case "delete-all":
                auditService.log("DELEGATES_DELETED_ALL", "DELEGATE", null,
                        sessionOpt.get().getId(),
                        "Bulk-deleted all delegates (" + delegates.size() + ").");
                presentationRepository.deleteAll(delegates);
                liveEventService.publish("delegate.changed", "");
                return "Success";
            default:
                return "Unknown action: " + action;
        }
        auditService.log("DELEGATES_BULK_ACTION", "DELEGATE", null,
                sessionOpt.get().getId(),
                "Bulk action \"" + action + "\" on " + delegates.size() + " delegates.");
        presentationRepository.saveAll(delegates);
        liveEventService.publish("delegate.changed", "");
        return "Success";
    }

    // Copies name-only delegates into the destination session, skipping duplicates
    // and respecting the session strength limit. Returns {imported, skipped}.
    private int[] importNames(Session destination, List<String> names) {
        java.util.Set<String> existing = new java.util.HashSet<>();
        for (Presentation p : presentationRepository.findBySessionOrderByIdAsc(destination)) {
            existing.add(p.getName().toLowerCase());
        }
        int capacity = destination.getStrength() > 0
                ? destination.getStrength() - existing.size()
                : Integer.MAX_VALUE;
        int imported = 0, skipped = 0;
        for (String raw : names) {
            String name = raw.trim();
            if (name.isEmpty()) continue;
            if (existing.contains(name.toLowerCase())) { skipped++; continue; }
            if (imported >= capacity) { skipped++; continue; }
            Presentation p = new Presentation();
            p.setName(name);
            p.setPresenting(false);
            p.setVoting(false);
            p.setSession(destination);
            presentationRepository.save(p);
            existing.add(name.toLowerCase());
            imported++;
        }
        return new int[] { imported, skipped };
    }

}
