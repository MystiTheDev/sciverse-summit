package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.data.Presentation;
import com.ishan.sciverse.summit.entity.DelegateMembership;
import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.entity.User;
import com.ishan.sciverse.summit.repository.DelegateMembershipRepository;
import com.ishan.sciverse.summit.repository.PresentationRepository;
import com.ishan.sciverse.summit.repository.SessionRepository;
import com.ishan.sciverse.summit.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Service
public class DelegateService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private PresentationRepository presentationRepository;

    @Autowired
    private DelegateMembershipRepository membershipRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AuditService auditService;

    /**
     * Validates a 6-character join code for the logged-in user and records the membership.
     * Returns a map with "success" (boolean) and a message.
     */
    @Transactional
    public Map<String, Object> joinSession(String username, String code) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (code == null || code.isBlank()) {
            result.put("success", false);
            result.put("message", "Please enter the 6-character session code.");
            auditService.log("JOIN_FAILED", "SESSION", null, null,
                    username + " tried to join with a blank code.");
            return result;
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            result.put("success", false);
            result.put("message", "Account not found.");
            auditService.log("JOIN_FAILED", "SESSION", null, null,
                    username + " tried to join: account not found.");
            return result;
        }

        Session session = sessionRepository.findByJoinCodeIgnoreCase(code.trim()).orElse(null);
        if (session == null) {
            result.put("success", false);
            result.put("message", "Invalid session code. Check with your chair for the correct code.");
            auditService.log("JOIN_FAILED", "SESSION", null, null,
                    username + " tried invalid code \"" + code.trim() + "\".");
            return result;
        }
        if (!Boolean.TRUE.equals(session.getActive())) {
            result.put("success", false);
            result.put("message", "This session is no longer active.");
            auditService.log("JOIN_FAILED", "SESSION", session.getId(), session.getId(),
                    username + " tried to join inactive session \"" + session.getName() + "\".");
            return result;
        }

        if (!isRegisteredInSession(session, user)) {
            String display = user.getUsername();
            if (user.getFullName() != null && !user.getFullName().isBlank()) {
                display = user.getFullName().trim() + " (" + user.getUsername() + ")";
            }
            result.put("success", false);
            result.put("message", "Neither your username nor your full name (\""
                    + display
                    + "\") is in this session's delegate records. Ask the chair to add either one first.");
            auditService.log("JOIN_FAILED", "SESSION", session.getId(), session.getId(),
                    username + " is not in the delegate records of \"" + session.getName() + "\".");
            return result;
        }

        // Upsert membership
        membershipRepository.findByUserAndSession(user, session).orElseGet(() -> {
            DelegateMembership m = new DelegateMembership();
            m.setUser(user);
            m.setSession(session);
            return membershipRepository.save(m);
        });

        // Notify the chair that a delegate has joined
        User chair = session.getUser();
        if (chair != null) {
            String joinedName = (user.getFullName() != null && !user.getFullName().isBlank())
                    ? user.getFullName().trim()
                    : user.getUsername();
            notificationService.notifyUser(chair, "Delegate Joined",
                    joinedName + " has joined the session \"" + session.getName() + "\".",
                    "DELEGATE_JOINED", "/");
        }

        auditService.log("SESSION_JOINED", "SESSION", session.getId(), session.getId(),
                (user.getFullName() != null ? user.getFullName() : username) + " joined.");
        result.put("success", true);
        result.put("message", "You have joined the session: " + session.getName());
        result.put("sessionName", session.getName());
        result.put("committee", session.getCommittee());
        result.put("topic", session.getTopic());
        return result;
    }

    /** True when the delegate's full name appears in the session's delegate records. */
    public boolean isRegisteredInSession(Session session, String fullName) {
        return presentationRepository.findBySessionOrderByIdAsc(session).stream()
                .anyMatch(p -> p.getName() != null && fullName != null
                        && p.getName().trim().equalsIgnoreCase(fullName.trim()));
    }

    /** True when EITHER the delegate's username or full name appears in the session's delegate records. */
    public boolean isRegisteredInSession(Session session, User user) {
        if (user == null) {
            return false;
        }
        return presentationRepository.findBySessionOrderByIdAsc(session).stream()
                .anyMatch(p -> matchesDelegate(p.getName(), user));
    }

    /** Matches a delegate-list name against either account identifier (case-insensitive). */
    private boolean matchesDelegate(String recordName, User user) {
        if (recordName == null || user == null) {
            return false;
        }
        String record = recordName.trim();
        if (user.getFullName() != null && !user.getFullName().isBlank()
                && record.equalsIgnoreCase(user.getFullName().trim())) {
            return true;
        }
        return user.getUsername() != null && record.equalsIgnoreCase(user.getUsername().trim());
    }

    /** Active session the delegate has joined, if any. */
    public Optional<Session> getJoinedSession(String username) {
        return userRepository.findByUsername(username)
                .flatMap(membershipRepository::findTopByUserOrderByIdDesc)
                .flatMap(m -> {
                    Session session = m.getSession();
                    if (session != null && Boolean.TRUE.equals(session.getActive())) {
                        return Optional.of(session);
                    }
                    return Optional.empty();
                });
    }

    public Optional<DelegateMembership> getMembership(String username, Session session) {
        return userRepository.findByUsername(username)
                .flatMap(u -> membershipRepository.findByUserAndSession(u, session));
    }

    /** The delegate's full name (used to match against Presentation records). */
    public Optional<String> getDelegateFullName(String username) {
        return userRepository.findByUsername(username).map(User::getFullName);
    }

    /** Finds the Presentation record for the given session+name, if present. */
    public Optional<Presentation> getDelegateRecord(Session session, String fullName) {
        return presentationRepository.findBySessionOrderByIdAsc(session).stream()
                .filter(p -> p.getName() != null && fullName != null
                        && p.getName().trim().equalsIgnoreCase(fullName.trim()))
                .findFirst();
    }

    /** Finds the Presentation record matching EITHER the account's username or full name. */
    public Optional<Presentation> getDelegateRecordForUser(Session session, String username) {
        return userRepository.findByUsername(username)
                .flatMap(user -> presentationRepository.findBySessionOrderByIdAsc(session).stream()
                        .filter(p -> matchesDelegate(p.getName(), user))
                        .findFirst());
    }

    /** Increments and persists the delegate's motion (or amendment) proposal counter. */
    public void incrementProposal(Session session, String fullName, boolean amendment) {
        getDelegateRecord(session, fullName).ifPresent(p -> {
            if (amendment) {
                p.setAmendmentProposals(p.getAmendmentProposals() + 1);
            } else {
                p.setMotionProposals(p.getMotionProposals() + 1);
            }
            presentationRepository.save(p);
        });
    }

    /** Same counter, resolved by EITHER username or full name. */
    public void incrementProposalForUser(Session session, String username, boolean amendment) {
        getDelegateRecordForUser(session, username).ifPresent(p -> {
            if (amendment) {
                p.setAmendmentProposals(p.getAmendmentProposals() + 1);
            } else {
                p.setMotionProposals(p.getMotionProposals() + 1);
            }
            presentationRepository.save(p);
        });
    }

    /** Returns a map of lowercase full name → membership for all delegates who joined the given session. */
    public Map<String, DelegateMembership> getJoinedMembersByName(Session session) {
        Map<String, DelegateMembership> result = new HashMap<>();
        for (DelegateMembership m : membershipRepository.findBySession(session)) {
            if (m.getUser() != null && m.getUser().getFullName() != null) {
                result.put(m.getUser().getFullName().trim().toLowerCase(), m);
            }
        }
        return result;
    }

    /** Removes the delegate's membership from their current session and notifies the chair. */
    @Transactional
    public Map<String, Object> leaveSession(String username) {
        Map<String, Object> result = new LinkedHashMap<>();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            result.put("success", false);
            result.put("message", "Account not found.");
            return result;
        }
        Optional<DelegateMembership> membershipOpt = membershipRepository.findTopByUserOrderByIdDesc(user);
        if (membershipOpt.isEmpty()) {
            result.put("success", false);
            result.put("message", "You are not currently in a session.");
            return result;
        }
        DelegateMembership membership = membershipOpt.get();
        Session session = membership.getSession();
        membershipRepository.delete(membership);
        auditService.log("SESSION_LEFT", "SESSION",
                session != null ? session.getId() : null,
                session != null ? session.getId() : null,
                (user.getFullName() != null ? user.getFullName() : username) + " left.");

        // Notify the chair
        if (session != null && session.getUser() != null) {
            String fullName = user.getFullName() != null ? user.getFullName() : username;
            notificationService.notifyUser(session.getUser(), "Delegate Left",
                    fullName + " has left the session \"" + session.getName() + "\".",
                    "DELEGATE_LEFT", "/");
        }

        result.put("success", true);
        result.put("message", "You have left the session.");
        return result;
    }
}
