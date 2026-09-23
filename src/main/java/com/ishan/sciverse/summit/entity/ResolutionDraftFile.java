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
@Table(name = "resolution_draft_files")
@Data
public class ResolutionDraftFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "resolution_id")
    private ResolutionDraft resolution;

    /** Relative path on disk of the uploaded file (PDF/DOC). */
    private String filePath;

    /** Original uploaded file name. */
    private String fileName;

    /** Kind of document: "PDF", "DOC" or "TEXT". */
    private String fileType;

    @Column(name = "uploaded_at")
    private java.time.LocalDateTime uploadedAt;

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        uploadedAt = java.time.LocalDateTime.now();
    }
}
