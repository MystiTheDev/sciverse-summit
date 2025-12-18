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

    // READ: Display list of all presentations
    @GetMapping("/")
    public String listPresentations(Model model) {
    	model.addAttribute("presentations", presentationRepository.findAllByOrderByIdDesc());
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
        // Fetch all Presentation names to be used for autocompletion in the queue
        List<String> presenterNames = presentationRepository.findAll().stream()
            .map(Presentation::getName)
            .distinct()
            .collect(Collectors.toList());
            
        // Convert list to a JSON string for easy use in JavaScript
        String namesJson = presenterNames.stream()
            .map(name -> "'" + name.replace("'", "\\'") + "'") // Simple escaping
            .collect(Collectors.joining(","));
            
        model.addAttribute("presenterNamesJson", namesJson);
        return "speakers";
    }
    
    @GetMapping("/motions")
    public String showMotions(Model model) {
        // Fetch all Presentation names to be used for autocompletion in the queue
        List<String> presenterNames = presentationRepository.findAll().stream()
            .map(Presentation::getName)
            .distinct()
            .collect(Collectors.toList());
            
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
        model.addAttribute("delegates", presentationRepository.findAll());
        return "stats";
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
    
}
