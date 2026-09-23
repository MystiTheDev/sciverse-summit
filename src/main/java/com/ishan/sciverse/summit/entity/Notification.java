package com.ishan.sciverse.summit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

/**
 * Server-side notification sent to a specific user (e.g. "voting is live").
 * New notification types can be added later through NotificationService.
 */
@Entity
@Table(name = "notifications")
@Data
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    /** Short headline, e.g. "Voting is live". */
    private String title;

    /** Full message shown in the notification centre. */
    @Column(columnDefinition = "TEXT")
    private String message;

    /** Machine-friendly type, e.g. "VOTING", "MOTION", "RESOLUTION", "GENERAL". */
    private String type = "GENERAL";

    /** Deep link the user is taken to when they click the notification. */
    private String link;

    /** True once the user has dismissed/read the notification. */
    private boolean read;

    /** Delivery status: PENDING, SENT, FAILED, RETRYING, DEAD_LETTER. */
    private String status = "PENDING";

    /** How many times the outbox retry job has attempted to resend. */
    @Column(name = "retry_count")
    private int retryCount = 0;

    /** Next scheduled retry time (null if not awaiting retry). */
    @Column(name = "next_retry_at")
    private java.time.LocalDateTime nextRetryAt;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
    }
}
