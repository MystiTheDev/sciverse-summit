package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.entity.User;
import com.ishan.sciverse.summit.repository.SessionRepository;
import com.ishan.sciverse.summit.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SessionService {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    public Session createSession(Session session) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        session.setUser(user);
        return sessionRepository.save(session);
    }

    public List<Session> getUserSessions() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return sessionRepository.findByUser(user);
    }

    public Optional<Session> getLatestSession() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return sessionRepository.findTopByUserOrderByIdDesc(user);
    }

    public Optional<Session> getActiveSession() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return sessionRepository.findTopByUserAndActiveTrueOrderByIdDesc(user);
    }

    public void endSession(Long sessionId) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setActive(false);
            sessionRepository.save(session);
        });
    }

    public void saveNotes(Long sessionId, String notes) {
        sessionRepository.findById(sessionId).ifPresent(session -> {
            session.setNotes(notes);
            sessionRepository.save(session);
        });
    }
}
