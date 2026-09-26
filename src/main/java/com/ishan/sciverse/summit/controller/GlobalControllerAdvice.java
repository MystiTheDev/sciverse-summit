package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.entity.User;
import com.ishan.sciverse.summit.service.SessionService;
import com.ishan.sciverse.summit.service.UserService;
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
    
    /**
     * Everything the sidebar session card needs, resolved once.
     * Replaces the old pattern of reading {@code currentSession}/{@code joinedSession}
     * and re-testing which one is non-null in a dozen Thymeleaf expressions.
     * Chairs see their active session; delegates see the one they joined.
     */
    public record SidebarSession(String name, String joinCode, String committee,
                                 String topic, int strength, int delegateCount,
                                 boolean isChair, String startedLabel, boolean active) {}

    @ModelAttribute("sidebarSession")
    public SidebarSession getSidebarSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return null;
        }
        try {
            User user = userRepository.findByUsername(authentication.getName()).orElse(null);
            boolean chair = user != null && UserService.isChairRole(user.getRole());

            Object session = chair
                    ? sessionService.getActiveSession().or(() -> sessionService.getLatestSession()).orElse(null)
                    : delegateService.getJoinedSession(authentication.getName()).orElse(null);

            if (session instanceof Session s) {
                int actual = presentationRepository.findBySessionOrderByIdAsc(s).size();
                return new SidebarSession(s.getName(), s.getJoinCode(), s.getCommittee(),
                        s.getTopic(), s.getStrength(), actual, chair,
                        formatStarted(s), Boolean.TRUE.equals(s.getActive()));
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    /** "26 Sep 2026, 14:05" — formatted here because thymeleaf-extras-java8time isn't on the classpath. */
    private static String formatStarted(Session s) {
        if (s.getCreatedAt() == null) {
            return "—";
        }
        return s.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"));
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
