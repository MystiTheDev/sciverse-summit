package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.dto.ImportFile;
import com.ishan.sciverse.summit.dto.ImportSession;
import com.ishan.sciverse.summit.dto.ImportUser;
import com.ishan.sciverse.summit.repository.UserRepository;
import com.ishan.sciverse.summit.service.DataTransferService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
public class DataTransferController {

    @Autowired
    private DataTransferService dataTransferService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.ishan.sciverse.summit.service.AuditService auditService;

    // ── EXPORT EVERYTHING ─────────────────────────────────────────────────

    @GetMapping("/settings/export")
    public void exportEverything(HttpServletResponse response) throws IOException {
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"sciverse-summit-backup.json\"");
        response.getWriter().write(dataTransferService.exportJson());
        auditService.log("DATA_EXPORTED", null, null, "Full backup exported as JSON.");
    }

    // ── IMPORT PAGE ───────────────────────────────────────────────────────

    @GetMapping("/settings/import")
    public String showImportPage() {
        return "data-import";
    }

    // ── PREVIEW: parse the file and list what it contains ────────────────

    @PostMapping("/api/import/preview")
    @ResponseBody
    public Map<String, Object> previewImport(@RequestParam("file") MultipartFile file) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            ImportFile parsed = dataTransferService.parse(file.getBytes());
            resp.put("valid", true);
            resp.put("app", parsed.getApp());
            resp.put("exportVersion", parsed.getExportVersion());
            resp.put("exportedAt", parsed.getExportedAt());
            resp.put("fileName", file.getOriginalFilename());

            List<Map<String, Object>> users = new ArrayList<>();
            int totalSessions = 0, totalDelegates = 0;
            for (ImportUser u : parsed.getUsers()) {
                int[] counts = dataTransferService.countForUser(u);
                int sCount = counts[0], dCount = counts[1];
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fullName", u.getFullName());
                m.put("username", u.getUsername());
                m.put("email", u.getEmail());
                m.put("sessionCount", sCount);
                m.put("delegateCount", dCount);

                String existsReason = "";
                if (u.getUsername() != null && !u.getUsername().isBlank()
                        && userRepository.findByUsername(u.getUsername()).isPresent()) {
                    existsReason = "username";
                } else if (u.getEmail() != null && !u.getEmail().isBlank()
                        && userRepository.findByEmail(u.getEmail()).isPresent()) {
                    existsReason = "email";
                }
                m.put("exists", !existsReason.isEmpty());
                m.put("existsReason", existsReason);

                users.add(m);
                totalSessions += sCount;
                totalDelegates += dCount;
            }
            resp.put("userCount", users.size());
            resp.put("sessionCount", totalSessions);
            resp.put("delegateCount", totalDelegates);
            resp.put("users", users);
        } catch (Exception ex) {
            resp.put("valid", false);
            resp.put("error", "Could not read the backup file: " + ex.getMessage());
        }
        return resp;
    }

    // ── IMPORT: only the selected users are written to the database ───────

    @PostMapping("/api/import")
    @ResponseBody
    public Map<String, Object> doImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "username", required = false) List<String> usernames) {
        Map<String, Object> resp = new LinkedHashMap<>();
        try {
            ImportFile parsed = dataTransferService.parse(file.getBytes());
            Set<String> selected = usernames == null ? new HashSet<>() : new HashSet<>(usernames);
            if (selected.isEmpty()) {
                resp.put("imported", 0);
                resp.put("skipped", 0);
                resp.put("message", "No users were selected.");
                resp.put("results", new ArrayList<>());
                return resp;
            }
            List<Map<String, Object>> results = dataTransferService.importUsers(parsed, selected);
            int imported = 0, skipped = 0;
            for (Map<String, Object> r : results) {
                if ("imported".equals(r.get("status"))) imported++;
                else skipped++;
            }
            resp.put("imported", imported);
            resp.put("skipped", skipped);
            resp.put("results", results);
            auditService.log("DATA_IMPORTED", null, null,
                    "Backup import: " + imported + " imported, " + skipped + " skipped.");
        } catch (Exception ex) {
            resp.put("error", "Import failed: " + ex.getMessage());
            auditService.log("DATA_IMPORT_FAILED", null, null,
                    "Backup import failed: " + ex.getMessage());
        }
        return resp;
    }
}
