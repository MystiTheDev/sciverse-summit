package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.service.DelegateService;
import com.ishan.sciverse.summit.service.ResolutionService;
import com.ishan.sciverse.summit.service.VoteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;
import java.util.Collections;
import java.util.Optional;

@Controller
public class DelegateController {

    @Autowired
    private DelegateService delegateService;

    @Autowired
    private ResolutionService resolutionService;

    @Autowired
    private VoteService voteService;

    /** Resolves the delegate's joined session for every delegate page. */
    @ModelAttribute
    public void delegateModel(Model model, Principal principal) {
        Optional<Session> joined = principal == null
                ? Optional.empty()
                : delegateService.getJoinedSession(principal.getName());
        model.addAttribute("joinedSession", joined.orElse(null));
    }

    @GetMapping("/delegate")
    public String home(Model model, Principal principal) {
        if (principal != null) {
            delegateService.getDelegateFullName(principal.getName())
                    .ifPresent(name -> model.addAttribute("delegateFullName", name));
        }
        return "delegate-home";
    }

    @GetMapping("/delegate/motions")
    public String motions(Model model, Principal principal) {
        Session session = (Session) model.getAttribute("joinedSession");
        if (session != null && principal != null) {
            delegateService.getDelegateFullName(principal.getName())
                    .ifPresent(name -> model.addAttribute("delegateFullName", name));
        }
        return "delegate-motions";
    }

    @GetMapping("/delegate/resolution")
    public String resolution(Model model, Principal principal) {
        Session session = (Session) model.getAttribute("joinedSession");
        model.addAttribute("resolutions",
                session != null ? resolutionService.listBySession(session) : Collections.emptyList());
        if (principal != null) {
            delegateService.getDelegateFullName(principal.getName())
                    .ifPresent(name -> model.addAttribute("delegateFullName", name));
        }
        return "delegate-resolution";
    }

    @GetMapping("/delegate/voting")
    public String voting(Model model, Principal principal) {
        Session session = (Session) model.getAttribute("joinedSession");
        if (session != null) {
            model.addAttribute("ballotOpen", voteService.isBallotOpen(session));
            model.addAttribute("ballotType", session.getBallotType());
            model.addAttribute("ballotRefId", session.getBallotRefId());
            model.addAttribute("tally", voteService.tally(session));
            if (principal != null) {
                String fullName = delegateService.getDelegateFullName(principal.getName()).orElse("");
                voteService.getMyVote(session, fullName)
                        .ifPresent(v -> model.addAttribute("myVote", v.getChoice()));
            }
        } else {
            model.addAttribute("ballotOpen", false);
            model.addAttribute("tally", Collections.emptyMap());
        }
        return "delegate-voting";
    }

    /** The delegate's own performance stats + chair evaluation & notes. */
    @GetMapping("/delegate/stats")
    public String stats(Model model, Principal principal) {
        Session session = (Session) model.getAttribute("joinedSession");
        if (session != null && principal != null) {
            delegateService.getDelegateFullName(principal.getName()).ifPresent(fullName -> {
                model.addAttribute("delegateFullName", fullName);
                delegateService.getDelegateRecordForUser(session, principal.getName()).ifPresent(record -> {
                    model.addAttribute("delegateRecord", record);
                    model.addAttribute("scoreRating", ratingFor(record.getMatrixScore()));
                });
            });
        }
        return "delegate-stats";
    }

    private String ratingFor(int total) {
        if (total >= 85) return "Outstanding";
        if (total >= 70) return "Excellent";
        if (total >= 55) return "Good";
        if (total >= 40) return "Developing";
        return "Needs Improvement";
    }
}
