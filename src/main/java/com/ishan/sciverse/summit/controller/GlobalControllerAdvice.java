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

    @ModelAttribute("currentSession")
    public Object getCurrentSession() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken) && authentication.isAuthenticated()) {
            return sessionService.getLatestSession().orElse(null);
        }
        return null;
    }
    
    @Autowired
    private com.ishan.sciverse.summit.repository.UserRepository userRepository;

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
}
