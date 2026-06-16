package com.ishan.sciverse.summit.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import com.ishan.sciverse.summit.data.Presentation;
import com.ishan.sciverse.summit.repository.PresentationRepository;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.Optional;

@Controller
public class PresentationController {

    @Autowired
    private PresentationRepository presentationRepository;
    
    @Autowired
    private com.ishan.sciverse.summit.service.SessionService sessionService;

    // READ: Display list of all presentations
    @GetMapping("/")
    public String listPresentations(Model model) {
        // Show delegates for current session only
        var sessionOpt = sessionService.getActiveSession()
                .or(() -> sessionService.getLatestSession());
        
        if (sessionOpt.isPresent()) {
            model.addAttribute("presentations", 
                presentationRepository.findBySessionOrderByIdAsc(sessionOpt.get()));
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
    public String showUnmodForm(Presentation presentation) {
        return "unmod"; // Corresponds to src/main/resources/templates/unmod.html
    }

    // CREATE/UPDATE: Handle form submission to save/update a presentation
    @PostMapping("/save")
    public String savePresentation(Presentation presentation) {
        // Link to current session if it's a new delegate
        if (presentation.getId() == null) {
            var sessionOpt = sessionService.getActiveSession()
                    .or(() -> sessionService.getLatestSession());
            sessionOpt.ifPresent(presentation::setSession);
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
        }
        presentationRepository.save(presentation);
        return "redirect:/"; // Redirect back to the list view
    }

    // DELETE: Delete a presentation and redirect back to list
    @GetMapping("/delete/{id}")
    public String deletePresentation(@PathVariable("id") long id) {
        Presentation presentation = presentationRepository.findById(id)
          .orElseThrow(() -> new IllegalArgumentException("Invalid presentation Id:" + id));
        
        presentationRepository.delete(presentation);
        return "redirect:/";
    }
    
 // ⬇️ NEW SPEAKERS' LIST PAGE HANDLER ⬇️
    @GetMapping("/speakers")
    public String showSpeakersList(Model model) {
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

        // Convert list to a JSON string for easy use in JavaScript
        String namesJson = presenterNames.stream()
            .map(name -> "'" + name.replace("'", "\\'") + "'") // Simple escaping
            .collect(Collectors.joining(","));

        model.addAttribute("presenterNamesJson", namesJson);
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

        // Convert list to a JSON string for easy use in JavaScript
        String namesJson = presenterNames.stream()
            .map(name -> "'" + name.replace("'", "\\'") + "'") // Simple escaping
            .collect(Collectors.joining(","));

        model.addAttribute("presenterNamesJson", namesJson);
        model.addAttribute("delegateList", presenterNames);
        return "motions";
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
            model.addAttribute("delegates", presentationRepository.findBySessionOrderByIdAsc(session));
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

    // API to update speaking stats (Duration only)
    @PostMapping("/api/stats/update-speaking")
    @ResponseBody
    public String updateSpeakingStats(@RequestParam("name") String name, @RequestParam("duration") long duration) {
        Optional<Presentation> delegateOpt = presentationRepository.findAll().stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst();

        if (delegateOpt.isPresent()) {
            Presentation delegate = delegateOpt.get();
            // Only update duration here
            delegate.setTotalSpeakingTime(delegate.getTotalSpeakingTime() + duration);
            presentationRepository.save(delegate);
            return "Success";
        }
        return "Delegate not found";
    }

    // API to increment times spoken
    @PostMapping("/api/stats/increment-spoken")
    @ResponseBody
    public String incrementTimesSpoken(@RequestParam("name") String name) {
        Optional<Presentation> delegateOpt = presentationRepository.findAll().stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst();

        if (delegateOpt.isPresent()) {
            Presentation delegate = delegateOpt.get();
            delegate.setTimesSpoken(delegate.getTimesSpoken() + 1);
            presentationRepository.save(delegate);
            return "Success";
        }
        return "Delegate not found";
    }

    // API to update motion stats
    @PostMapping("/api/stats/update-motion")
    @ResponseBody
    public String updateMotionStats(@RequestParam("name") String name, @RequestParam("type") String type) {
        Optional<Presentation> delegateOpt = presentationRepository.findAll().stream()
                .filter(p -> p.getName().equalsIgnoreCase(name))
                .findFirst();

        if (delegateOpt.isPresent()) {
            Presentation delegate = delegateOpt.get();
            if ("Introduce amendment".equalsIgnoreCase(type)) {
                delegate.setAmendmentProposals(delegate.getAmendmentProposals() + 1);
            } else {
                delegate.setMotionProposals(delegate.getMotionProposals() + 1);
            }
            presentationRepository.save(delegate);
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
            @RequestParam("leadershipMatrix") int leadershipMatrix) {
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
            presentationRepository.save(p);
            return "Success";
        }
        return "Delegate not found";
    }
    
}
