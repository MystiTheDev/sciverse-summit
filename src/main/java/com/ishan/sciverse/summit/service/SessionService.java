package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.data.Presentation;
import com.ishan.sciverse.summit.entity.DelegateMembership;
import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.entity.User;
import com.ishan.sciverse.summit.repository.BallotRecordRepository;
import com.ishan.sciverse.summit.repository.DelegateMembershipRepository;
import com.ishan.sciverse.summit.repository.MotionRepository;
import com.ishan.sciverse.summit.repository.PresentationRepository;
import com.ishan.sciverse.summit.repository.ResolutionDraftRepository;
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

    @Autowired
    private DelegateMembershipRepository membershipRepository;

    @Autowired
    private MotionRepository motionRepository;

    @Autowired
    private ResolutionDraftRepository resolutionDraftRepository;

    @Autowired
    private com.ishan.sciverse.summit.repository.VoteRepository voteRepository;

    @Autowired
    private BallotRecordRepository ballotRecordRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AuditService auditService;

    private static final String JOIN_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    /** Notify every joined delegate that the session has ended, then remove all memberships. */
    private void notifyAndKickDelegates(Session session) {
        List<DelegateMembership> memberships = membershipRepository.findBySession(session);
        for (DelegateMembership m : memberships) {
            if (m.getUser() != null) {
                notificationService.notifyUser(
                        m.getUser(),
                        "Session Ended",
                        "The chair has ended the session \"" + session.getName() + "\".",
                        "SESSION_ENDED",
                        null);
            }
        }
        membershipRepository.deleteAll(memberships);
    }

    /** Generates a 6-character join code (no ambiguous 0/O/1/I/L). */
    public static String generateJoinCode() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(JOIN_CODE_ALPHABET.charAt(random.nextInt(JOIN_CODE_ALPHABET.length())));
        }
        return sb.toString();
    }

    /** Returns a unique join code not already in use by another session. */
    public String generateUniqueJoinCode() {
        String code;
        do {
            code = generateJoinCode();
        } while (sessionRepository.findByJoinCodeIgnoreCase(code).isPresent());
        return code;
    }

    /** Ensures the session has a join code, generating one if missing (backfill for legacy/imported sessions). */
    public Session ensureJoinCode(Session session) {
        if (session.getJoinCode() == null || session.getJoinCode().isBlank()) {
            session.setJoinCode(generateUniqueJoinCode());
            sessionRepository.save(session);
        }
        return session;
    }

    public Session createSession(Session session) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        session.setUser(user);
        session.setJoinCode(generateUniqueJoinCode());
        Session saved = sessionRepository.save(session);
        auditService.log("SESSION_CREATED", "SESSION", saved.getId(), saved.getId(),
                "Created session \"" + saved.getName() + "\" (" + saved.getCommittee() + ").");
        return saved;
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

    public List<Session> getActiveSessions() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return sessionRepository.findByUserAndActiveTrueOrderByIdDesc(user);
    }

    public int countActiveSessions() {
        return getActiveSessions().size();
    }

    public Optional<Session> getOwnedSession(Long sessionId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return sessionRepository.findById(sessionId)
                .filter(s -> s.getUser() != null && username.equals(s.getUser().getUsername()));
    }

    public void endSession(Long sessionId, String ebReview) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        sessionRepository.findById(sessionId).ifPresent(session -> {
            if (session.getUser() != null && username.equals(session.getUser().getUsername())) {
                notifyAndKickDelegates(session);
                session.setActive(false);
                if (ebReview != null && !ebReview.isBlank()) {
                    session.setEbReview(ebReview.trim());
                }
                sessionRepository.save(session);
                auditService.log("SESSION_ENDED", "SESSION", session.getId(), session.getId(),
                        "Ended session \"" + session.getName() + "\".");
            }
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
            auditService.log("SESSION_NOTES_UPDATED", "SESSION", sessionId, sessionId,
                    "Updated notes for session \"" + session.getName() + "\".");
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
        for (Session session : userSessions) {
            notifyAndKickDelegates(session);
            purgeSessionChildren(session);
        }
        sessionRepository.deleteAll(userSessions);
        auditService.log("SESSIONS_DELETED_ALL", "SESSION", null, null,
                "Deleted all sessions (" + userSessions.size() + ").");
    }

    @Transactional
    public void deleteSession(Long sessionId) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        sessionRepository.findById(sessionId).ifPresent(session -> {
            if (session.getUser().getId().equals(user.getId())) {
                notifyAndKickDelegates(session);
                purgeSessionChildren(session);
                auditService.log("SESSION_DELETED", "SESSION", sessionId, sessionId,
                        "Deleted session \"" + session.getName() + "\".");
                sessionRepository.delete(session);
            }
        });
    }

    /**
     * Removes every row that references the session so the delete does not
     * trip foreign keys. Resolution files go away via the draft cascade.
     */
    private void purgeSessionChildren(Session session) {
        voteRepository.deleteAll(voteRepository.findBySession(session));
        ballotRecordRepository.deleteAll(ballotRecordRepository.findBySessionOrderByIdDesc(session));
        motionRepository.deleteAll(motionRepository.findBySessionOrderByIdDesc(session));
        resolutionDraftRepository.deleteAll(resolutionDraftRepository.findBySessionOrderByIdDesc(session));
        List<Presentation> presentations = presentationRepository.findBySession(session);
        if (presentations != null && !presentations.isEmpty()) {
            presentationRepository.deleteAll(presentations);
        }
    }
}
