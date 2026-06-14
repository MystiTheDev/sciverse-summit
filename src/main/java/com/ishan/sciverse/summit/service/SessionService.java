package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.data.Presentation;
import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.entity.User;
import com.ishan.sciverse.summit.repository.PresentationRepository;
import com.ishan.sciverse.summit.repository.SessionRepository;
import com.ishan.sciverse.summit.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class SessionService {

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PresentationRepository presentationRepository;

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
        System.out.println("DEBUG SessionService: Finding session with ID: " + sessionId);
        sessionRepository.findById(sessionId).ifPresentOrElse(session -> {
            System.out.println("DEBUG SessionService: Found session: " + session.getName());
            System.out.println("DEBUG SessionService: Old notes: " + session.getNotes());
            session.setNotes(notes);
            sessionRepository.save(session);
            System.out.println("DEBUG SessionService: New notes saved: " + notes);
        }, () -> {
            System.err.println("ERROR SessionService: Session not found with ID: " + sessionId);
        });
    }

    public Optional<Session> getSessionById(Long sessionId) {
        return sessionRepository.findById(sessionId);
    }

    @Transactional
    public void deleteAllSessions() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        List<Session> userSessions = sessionRepository.findByUser(user);
        // Delete all related presentations first to avoid FK constraint violations
        for (Session session : userSessions) {
            List<Presentation> presentations = presentationRepository.findBySession(session);
            if (presentations != null && !presentations.isEmpty()) {
                presentationRepository.deleteAll(presentations);
            }
        }
        sessionRepository.deleteAll(userSessions);
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        sessionRepository.findById(sessionId).ifPresent(session -> {
            // Only delete if the session belongs to the current user
            if (session.getUser().getId().equals(user.getId())) {
                // Delete related presentations first to avoid FK constraint violations
                List<Presentation> presentations = presentationRepository.findBySession(session);
                if (presentations != null && !presentations.isEmpty()) {
                    presentationRepository.deleteAll(presentations);
                }
                sessionRepository.delete(session);
            }
        });
    }
}
