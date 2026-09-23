package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.entity.AuditLog;
import com.ishan.sciverse.summit.repository.AuditRepository;
import com.ishan.sciverse.summit.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fire-and-forget audit trail. Every method swallows its own failures so
 * auditing can never break the action being audited. Never logs secrets.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    static final int MAX_DETAILS = 500;

    @Autowired
    private AuditRepository auditRepository;

    @Autowired
    private UserRepository userRepository;

    public void log(String action, String entityType, Object entityId, Long sessionId, String details) {
        try {
            String username = "SYSTEM";
            String role = "SYSTEM";
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()
                        && !"anonymousUser".equals(String.valueOf(auth.getPrincipal()))) {
                    username = auth.getName();
                    role = userRepository.findByUsername(username)
                            .map(u -> u.getRole() != null ? u.getRole() : "UNKNOWN")
                            .orElse("UNKNOWN");
                }
            } catch (Exception ignored) {
                // fall through with SYSTEM identity
            }
            AuditLog entry = new AuditLog();
            entry.setActorUsername(username);
            entry.setActorRole(role);
            entry.setAction(action);
            entry.setEntityType(entityType);
            entry.setEntityId(entityId == null ? null : String.valueOf(entityId));
            entry.setSessionId(sessionId);
            if (details != null && details.length() > MAX_DETAILS) {
                details = details.substring(0, MAX_DETAILS);
            }
            entry.setDetails(details);
            auditRepository.save(entry);
        } catch (Exception e) {
            log.warn("Audit write failed for action={}: {}", action, e.getMessage());
        }
    }

    public void log(String action, String entityType, Object entityId, String details) {
        log(action, entityType, entityId, null, details);
    }

    public void log(String action, String details) {
        log(action, null, null, null, details);
    }

    public List<AuditLog> search(Long sessionId, String action, String actor, String q) {
        try {
            String a = (action == null || action.isBlank()) ? null : action.trim();
            String u = (actor == null || actor.isBlank()) ? null : actor.trim();
            String query = (q == null || q.isBlank()) ? null : q.trim();
            List<AuditLog> out = auditRepository.search(sessionId, a, u, query);
            return out.size() > 500 ? out.subList(0, 500) : out;
        } catch (Exception e) {
            log.warn("Audit search failed: {}", e.getMessage());
            return List.of();
        }
    }

    /** CSV for one session (header + rows), used by the viewer and history exports. */
    public String toCsv(Long sessionId) {
        StringBuilder sb = new StringBuilder();
        sb.append("Time,Actor,Role,Action,Entity,Entity ID,Session ID,Details\n");
        java.time.format.DateTimeFormatter dtf =
                java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        for (AuditLog e : search(sessionId, null, null, null)) {
            sb.append(csv(e.getCreatedAt() != null ? e.getCreatedAt().format(dtf) : "")).append(',')
                    .append(csv(e.getActorUsername())).append(',')
                    .append(csv(e.getActorRole())).append(',')
                    .append(csv(e.getAction())).append(',')
                    .append(csv(e.getEntityType())).append(',')
                    .append(csv(e.getEntityId())).append(',')
                    .append(e.getSessionId() != null ? e.getSessionId() : "").append(',')
                    .append(csv(e.getDetails())).append('\n');
        }
        return sb.toString();
    }

    private String csv(String value) {
        if (value == null || value.isBlank()) return "\"\"";
        return "\"" + value.replace("\"", "\"\"").replace("\n", " ").replace("\r", "") + "\"";
    }

    public List<String> distinctActions() {
        try {
            return auditRepository.findAll().stream()
                    .map(AuditLog::getAction)
                    .filter(s -> s != null && !s.isBlank())
                    .distinct()
                    .sorted()
                    .toList();
        } catch (Exception e) {
            return List.of();
        }
    }
}
