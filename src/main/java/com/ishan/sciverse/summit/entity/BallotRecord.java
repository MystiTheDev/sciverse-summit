package com.ishan.sciverse.summit.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "ballot_records")
@Data
public class BallotRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    /** "MOTION" or "RESOLUTION". */
    @Column(name = "ballot_type", nullable = false)
    private String ballotType;

    /** Reference id of the motion or resolution. */
    @Column(name = "ballot_ref_id", nullable = false)
    private Long ballotRefId;

    /** Cached display title (motion type or resolution title). */
    @Column(columnDefinition = "TEXT")
    private String title;

    /** OPEN or CLOSED. */
    @Column(nullable = false)
    private String status;

    /** PASSED or FAILED (null while open). */
    private String result;

    @Column(name = "for_count")
    private int forCount;

    @Column(name = "against_count")
    private int againstCount;

    @Column(name = "neutral_count")
    private int neutralCount;

    @Column(name = "total_votes")
    private int totalVotes;

    @Column(name = "opened_at")
    private LocalDateTime openedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @PrePersist
    protected void onCreate() {
        openedAt = LocalDateTime.now();
    }
}
