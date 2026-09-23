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

@Entity
@Table(name = "votes")
@Data
public class Vote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id")
    private Session session;

    /** What the ballot is about: "MOTION" or "RESOLUTION". */
    @Column(name = "ballot_type")
    private String ballotType;

    /** Reference id of the motion or resolution being voted on. */
    @Column(name = "ballot_ref_id")
    private Long ballotRefId;

    /** Name of the delegate who voted (matches Presentation.name). */
    private String delegateName;

    /** FOR / AGAINST / ABSTAIN. */
    private String choice;

    @Column(name = "cast_at")
    private java.time.LocalDateTime castAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        castAt = java.time.LocalDateTime.now();
    }
}
