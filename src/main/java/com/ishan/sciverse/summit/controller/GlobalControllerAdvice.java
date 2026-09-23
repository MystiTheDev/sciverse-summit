package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.service.SessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private com.ishan.sciverse.summit.service.DelegateService delegateService;

    @Autowired
    private com.ishan.sciverse.summit.repository.UserRepository userRepository;

    /** True when the logged-in user is a Chair or Admin (drives sidebar contents). */
    @ModelAttribute("isChair")
    public boolean isChair() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated()) {
            return userRepository.findByUsername(authentication.getName())
                    .map(u -> com.ishan.sciverse.summit.service.UserService.isChairRole(u.getRole()))
                    .orElse(false);
        }
        return false;
    }

    /** The session a delegate has joined (null for chairs and users who haven't joined). */
    @ModelAttribute("joinedSession")
    public Object getJoinedSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated()) {
            try {
                return delegateService.getJoinedSession(authentication.getName()).orElse(null);
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    @ModelAttribute("currentSession")
    public Object getCurrentSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated()) {
            // Try to get active session first, fall back to latest session
            return sessionService.getActiveSession()
                    .or(() -> sessionService.getLatestSession())
                    .orElse(null);
        }
        return null;
    }
    
    @Autowired
    private com.ishan.sciverse.summit.repository.PresentationRepository presentationRepository;

    /** Number of delegates in the current session — used by sidebar to gate Speakers/Motions links. */
    @ModelAttribute("delegateCount")
    public int getDelegateCount() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated()) {
                var sessionOpt = sessionService.getActiveSession()
                        .or(() -> sessionService.getLatestSession());
                if (sessionOpt.isPresent()) {
                    return presentationRepository.findBySessionOrderByIdAsc(sessionOpt.get()).size();
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    @ModelAttribute("currentUserEmail")
    public String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated()) {
             return userRepository.findByUsername(authentication.getName())
                 .map(com.ishan.sciverse.summit.entity.User::getEmail)
                 .orElse(authentication.getName());
        }
        return null;
    }

    @ModelAttribute("currentUsername")
    public String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return null;
    }

    @ModelAttribute("currentUserFullName")
    public String getCurrentUserFullName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated()) {
            return userRepository.findByUsername(authentication.getName())
                .map(com.ishan.sciverse.summit.entity.User::getFullName)
                .orElse(null);
        }
        return null;
    }

    @ModelAttribute("currentUserMaskedEmail")
    public String getCurrentUserMaskedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated()) {
            return userRepository.findByUsername(authentication.getName()).map(u -> {
                String e = u.getEmail();
                if (e == null || !e.contains("@")) return e;
                String[] p = e.split("@", 2);
                String local = p[0];
                if (local.length() <= 2) return local.charAt(0) + "***@" + p[1];
                return local.charAt(0) + "***" + local.charAt(local.length() - 1) + "@" + p[1];
            }).orElse(null);
        }
        return null;
    }
}
