package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.entity.ResolutionDraft;
import com.ishan.sciverse.summit.entity.ResolutionDraftFile;
import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.repository.ResolutionDraftRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ResolutionService {

    private static final String UPLOAD_DIR = "data/resolutions";

    @Autowired
    private ResolutionDraftRepository resolutionDraftRepository;

    @Autowired
    private AuditService auditService;

    public ResolutionDraft submit(Session session, String title, String primaryAuthor2, String sponsors,
                                  String description, String operativeClauses, String submitter,
                                  MultipartFile[] files) throws IOException {
        ResolutionDraft draft = new ResolutionDraft();
        draft.setSession(session);
        draft.setTitle(title != null ? title.trim() : "");
        draft.setPrimaryAuthor2(primaryAuthor2 != null ? primaryAuthor2.trim() : "");
        draft.setSponsors(sponsors != null ? sponsors.trim() : "");
        draft.setDescription(description != null ? description.trim() : "");
        draft.setOperativeClauses(operativeClauses != null ? operativeClauses.trim() : "");
        draft.setSubmitter(submitter != null ? submitter.trim() : "");

        Path dir = Paths.get(UPLOAD_DIR, String.valueOf(session.getId()));
        Files.createDirectories(dir);

        List<MultipartFile> uploaded = new ArrayList<>();
        if (files != null) {
            for (MultipartFile file : files) {
                if (file != null && !file.isEmpty()) {
                    uploaded.add(file);
                }
            }
        }
        for (MultipartFile file : uploaded) {
            String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "draft" : file.getOriginalFilename());
            String storedName = System.currentTimeMillis() + "_" + sanitizeFileName(original);

            Path target = dir.resolve(storedName);
            file.transferTo(target.toAbsolutePath());

            ResolutionDraftFile stored = new ResolutionDraftFile();
            stored.setFilePath(UPLOAD_DIR + "/" + session.getId() + "/" + storedName);
            stored.setFileName(original);
            stored.setFileType(fileTypeOf(original));
            draft.addFile(stored);
        }

        // Keep the legacy single-file columns pointing at the first uploaded file
        // so existing endpoints and any older clients keep working.
        if (!draft.getFiles().isEmpty()) {
            ResolutionDraftFile first = draft.getFiles().get(0);
            draft.setFilePath(first.getFilePath());
            draft.setFileName(first.getFileName());
        }
        ResolutionDraft saved = resolutionDraftRepository.save(draft);
        auditService.log("RESOLUTION_SUBMITTED", "RESOLUTION", saved.getId(), session.getId(),
                submitter + " submitted resolution \"" + saved.getTitle() + "\""
                        + (saved.getFiles().isEmpty() ? "" : " (" + saved.getFiles().size() + " file(s))") + ".");
        return saved;
    }

    /** Classifies a file name as PDF / DOC / TEXT for the admin's draft list. */
    private String fileTypeOf(String name) {
        String lower = (name == null ? "" : name).toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "PDF";
        }
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return "DOC";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md")) {
            return "TEXT";
        }
        return "FILE";
    }

    public List<ResolutionDraft> listBySession(Session session) {
        return resolutionDraftRepository.findBySessionOrderByIdDesc(session);
    }

    public Optional<ResolutionDraft> getById(Long id) {
        return resolutionDraftRepository.findWithFilesById(id);
    }

    private String sanitizeFileName(String name) {
        String cleaned = name.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isBlank() ? "draft" : cleaned;
    }
}
