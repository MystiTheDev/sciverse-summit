package com.ishan.sciverse.summit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

/** Immutable record of a significant action. Never stores secrets (no passwords). */
@Entity
@Table(name = "audit_log")
@Data
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    /** Username from the security context, or "SYSTEM"/"ANONYMOUS". */
    @Column(name = "actor_username")
    private String actorUsername;

    @Column(name = "actor_role")
    private String actorRole;

    /** e.g. SESSION_CREATED, MOTION_DECIDED, VOTE_CAST. */
    @Column(name = "action")
    private String action;

    /** e.g. SESSION, DELEGATE, MOTION, BALLOT, RESOLUTION, USER. */
    @Column(name = "entity_type")
    private String entityType;

    @Column(name = "entity_id")
    private String entityId;

    @Column(name = "session_id")
    private Long sessionId;

    /** Short human-readable summary (capped by AuditService). Never secrets. */
    @Column(columnDefinition = "TEXT")
    private String details;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
    }
}
