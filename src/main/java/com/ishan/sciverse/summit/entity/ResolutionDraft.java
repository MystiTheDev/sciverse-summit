package com.ishan.sciverse.summit.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "resolution_drafts")
@Data
public class ResolutionDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id")
    private Session session;

    private String title;

    /** Comma-separated names of co-sponsors. */
    @Column(columnDefinition = "TEXT")
    private String sponsors;

    /** Delegate who submitted the draft (primary author 1). */
    private String submitter;

    /** Optional second primary author. */
    @Column(name = "primary_author2")
    private String primaryAuthor2;

    /** Short summary / description of the draft resolution. */
    @Column(columnDefinition = "TEXT")
    private String description;

    /** Operative clauses of the draft resolution (free text). */
    @Column(name = "operative_clauses", columnDefinition = "TEXT")
    private String operativeClauses;

    /** Relative path on disk of the uploaded draft (PDF/DOC). */
    private String filePath;

    /** Original uploaded file name. */
    private String fileName;

    @Column(name = "uploaded_at")
    private java.time.LocalDateTime uploadedAt;

    /** All uploaded draft documents (e.g. a PDF version and a Word version). */
    @OneToMany(mappedBy = "resolution", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<ResolutionDraftFile> files = new ArrayList<>();

    public void addFile(ResolutionDraftFile file) {
        file.setResolution(this);
        files.add(file);
    }

    @jakarta.persistence.PrePersist
    protected void onCreate() {
        uploadedAt = java.time.LocalDateTime.now();
    }
}
