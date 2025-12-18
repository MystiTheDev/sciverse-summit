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

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
    }
}
