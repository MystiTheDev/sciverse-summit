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
@Table(name = "delegate_memberships")
@Data
public class DelegateMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name = "session_id")
    private Session session;

    @jakarta.persistence.Column(name = "joined_at")
    private java.time.LocalDateTime joinedAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        joinedAt = java.time.LocalDateTime.now();
    }
}
