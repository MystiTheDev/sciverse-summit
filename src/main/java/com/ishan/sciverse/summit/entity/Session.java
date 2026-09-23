package com.ishan.sciverse.summit.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Entity
@Table(name = "sessions")
@Data
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Session name is required")
    private String name;

    @NotBlank(message = "Committee is required")
    private String committee;

    @Min(value = 1, message = "Strength must be at least 1")
    private int strength;

    @NotBlank(message = "Topic is required")
    private String topic;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "created_at")
    private java.time.LocalDateTime createdAt;

    @Column(name = "is_active")
    private Boolean active = true;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "eb_review", columnDefinition = "TEXT")
    private String ebReview;

    /** 6-character alphanumeric code delegates use to join this session. */
    @Column(unique = true)
    private String joinCode;

    /** True when the chair has opened a ballot for this session (delegate voting). */
    @Column(name = "ballot_open")
    private Boolean ballotOpen = false;

    /** What the open ballot is about: "MOTION" or "RESOLUTION". */
    @Column(name = "ballot_type")
    private String ballotType;

    /** Reference id of the motion or resolution the ballot is about. */
    @Column(name = "ballot_ref_id")
    private Long ballotRefId;

    /** Live segment announced to delegates: "NONE", "CANDOR" or "KOLLOQUIUM". */
    @Column(name = "segment_type")
    private String segmentType = "NONE";

    /** Display label for the live segment, e.g. "Candor Session". */
    @Column(name = "segment_label")
    private String segmentLabel;

    /** Total segment length in seconds (for progress bars). */
    @Column(name = "segment_total_secs")
    private Integer segmentTotalSecs;

    /** Wall-clock time the running segment ends (null while paused). */
    @Column(name = "segment_ends_at")
    private java.time.LocalDateTime segmentEndsAt;

    /** True while the chair has paused the segment. */
    @Column(name = "segment_paused")
    private Boolean segmentPaused = false;

    /** Seconds remaining (source of truth while paused; snapshot otherwise). */
    @Column(name = "segment_remaining_secs")
    private Integer segmentRemainingSecs;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
    }
}
