package com.ishan.sciverse.summit.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "motions")
@Data
public class Motion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id")
    private Session session;

    /** Type of motion raised, e.g. "Open Kolloqium". */
    private String type;

    /** Optional delegate-supplied topic/detail, e.g. "Climate, 90s speaking time". */
    @jakarta.persistence.Column(name = "detail", length = 500)
    private String detail;

    /** Name of the delegate who raised the motion. */
    private String proposer;

    @jakarta.persistence.Column(name = "raised_at")
    private java.time.LocalDateTime raisedAt;

    /** PENDING / ACCEPTED / REJECTED. */
    private String status = "PENDING";

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        raisedAt = java.time.LocalDateTime.now();
    }
}
