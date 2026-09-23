package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.entity.Motion;
import com.ishan.sciverse.summit.entity.ResolutionDraft;
import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.service.DelegateService;
import com.ishan.sciverse.summit.service.LiveEventService;
import com.ishan.sciverse.summit.service.MotionService;
import com.ishan.sciverse.summit.service.NotificationService;
import com.ishan.sciverse.summit.service.ResolutionService;
import com.ishan.sciverse.summit.service.SessionService;
import com.ishan.sciverse.summit.service.UserService;
import com.ishan.sciverse.summit.service.VoteService;
import com.ishan.sciverse.summit.service.AuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class ChairLiveController {

    private static final Logger log = LoggerFactory.getLogger(ChairLiveController.class);

    @Autowired
    private SessionService sessionService;

    @Autowired
    private MotionService motionService;

    @Autowired
    private VoteService voteService;

    @Autowired
    private ResolutionService resolutionService;

    @Autowired
    private com.ishan.sciverse.summit.service.ResolutionFilePreviewService resolutionFilePreviewService;

    @Autowired
    private com.ishan.sciverse.summit.repository.ResolutionDraftFileRepository resolutionDraftFileRepository;

    @Autowired
    private com.ishan.sciverse.summit.service.BallotRecordService ballotRecordService;

    @Autowired
    private com.ishan.sciverse.summit.repository.PresentationRepository presentationRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private LiveEventService liveEventService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private DelegateService delegateService;

    @Autowired
    private com.ishan.sciverse.summit.repository.UserRepository userRepository;

    // ── CHAIR VOTING PAGE ──────────────────────────────────────────────────

    @GetMapping("/voting")
    public String showVoting(Model model) {
        Optional<Session> sessionOpt = sessionService.getActiveSession()
                .or(() -> sessionService.getLatestSession());
        if (sessionOpt.isPresent()) {
            Session session = sessionOpt.get();
            model.addAttribute("votingSession", session);
            model.addAttribute("ballotOpen", voteService.isBallotOpen(session));
            model.addAttribute("ballotType", session.getBallotType());
            model.addAttribute("ballotRefId", session.getBallotRefId());
            model.addAttribute("ballotLabel", ballotLabel(session));
            model.addAttribute("motions", motionService.listBySession(session));
            model.addAttribute("resolutions", resolutionService.listBySession(session));
            model.addAttribute("tally", voteService.tally(session));
        } else {
            model.addAttribute("votingSession", null);
            model.addAttribute("ballotOpen", false);
            model.addAttribute("motions", new ArrayList<>());
            model.addAttribute("resolutions", new ArrayList<>());
        }
        return "voting";
    }

    private String ballotLabel(Session session) {
        if (session.getBallotRefId() == null) {
            return "";
        }
        if ("MOTION".equalsIgnoreCase(session.getBallotType())) {
            return motionService.getById(session.getBallotRefId())
                    .map(m -> "Motion: " + m.getType()).orElse("Motion");
        }
        if ("RESOLUTION".equalsIgnoreCase(session.getBallotType())) {
            return resolutionService.getById(session.getBallotRefId())
                    .map(r -> "Resolution: " + r.getTitle()).orElse("Resolution");
        }
        return "";
    }

    // ── LIVE MOTIONS (chair side) ──────────────────────────────────────────

    @GetMapping("/api/motions/live")
    @ResponseBody
    public List<Map<String, Object>> liveMotions(@RequestParam(required = false) Long sessionId) {
        Optional<Session> sessionOpt = sessionId != null
                ? sessionService.getOwnedSession(sessionId).or(() -> sessionService.getSessionById(sessionId))
                : sessionService.getActiveSession().or(() -> sessionService.getLatestSession());
        if (sessionOpt.isEmpty()) {
            return new ArrayList<>();
        }
        Session session = sessionOpt.get();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Motion m : motionService.listLiveBySession(session)) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", m.getId());
            o.put("name", m.getType());
            o.put("detail", m.getDetail());
            o.put("proposer", m.getProposer());
            o.put("time", m.getRaisedAt() != null ? m.getRaisedAt().format(fmt) : "");
            o.put("status", m.getStatus());
            boolean ballotOn = voteService.isBallotOpen(session)
                    && "MOTION".equalsIgnoreCase(session.getBallotType())
                    && m.getId().equals(session.getBallotRefId());
            o.put("votingOpen", ballotOn);
            out.add(o);
        }
        return out;
    }

    @GetMapping("/api/motions/history")
    @ResponseBody
    public List<Map<String, Object>> motionHistory(@RequestParam(required = false) Long sessionId) {
        Optional<Session> sessionOpt = sessionId != null
                ? sessionService.getOwnedSession(sessionId).or(() -> sessionService.getSessionById(sessionId))
                : sessionService.getActiveSession().or(() -> sessionService.getLatestSession());
        if (sessionOpt.isEmpty()) {
            return new ArrayList<>();
        }
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        List<Map<String, Object>> out = new ArrayList<>();
        for (Motion m : motionService.listBySession(sessionOpt.get())) {
            String status = m.getStatus() == null ? "PENDING" : m.getStatus().toUpperCase();
            if ("PENDING".equals(status)) {
                continue;
            }
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", m.getId());
            o.put("name", m.getType());
            o.put("detail", m.getDetail());
            o.put("proposer", m.getProposer());
            o.put("time", m.getRaisedAt() != null ? m.getRaisedAt().format(fmt) : "");
            o.put("status", status);
            out.add(o);
        }
        return out;
    }

    /**
     * Chair decides a motion directly: marks it ACCEPTED or DECLINED and
     * notifies every joined delegate of the outcome. Voting is not used for
     * motions — only for resolutions.
     */
    @PostMapping("/api/motions/decide")
    @ResponseBody
    public Map<String, Object> decideMotion(@RequestParam Long motionId, @RequestParam String decision) {
        Map<String, Object> result = new LinkedHashMap<>();
        Motion motion = motionService.getById(motionId).orElse(null);
        if (motion == null || motion.getSession() == null) {
            result.put("success", false);
            result.put("message", "Motion not found.");
            return result;
        }
        String status = "DECLINED";
        if ("ACCEPTED".equalsIgnoreCase(decision)) {
            status = "ACCEPTED";
        }
        motionService.updateStatus(motionId, status);
        Session session = motion.getSession();
        auditService.log("MOTION_DECIDED", "MOTION", motionId,
                session != null ? session.getId() : null,
                "Motion \"" + motion.getType() + "\" by "
                        + (motion.getProposer() != null ? motion.getProposer() : "?")
                        + " was " + status.toLowerCase() + ".");
        try {
            if (voteService.isBallotOpen(session)
                    && "MOTION".equalsIgnoreCase(session.getBallotType())
                    && motion.getId().equals(session.getBallotRefId())) {
                voteService.closeBallot(session.getId());
            }
        } catch (Exception e) {
            log.warn("closeBallot failed for session {} but continuing: {}", session.getId(), e.getMessage());
        }
        String verb = "ACCEPTED".equals(status) ? "accepted" : "declined";
        String proposerName = motion.getProposer();
        boolean notifiedProposer = false;
        if (proposerName != null && !proposerName.isBlank()) {
            var membersByName = delegateService.getJoinedMembersByName(session);
            var membership = membersByName.get(proposerName.trim().toLowerCase());
            if (membership != null && membership.getUser() != null) {
                notificationService.notifyUserQuietly(
                        membership.getUser(),
                        "Motion " + verb,
                        "The chair has " + verb + " your motion \"" + motion.getType() + "\".",
                        "MOTION",
                        "/delegate/motions");
                notifiedProposer = true;
                log.info("Motion {} notification sent to proposer '{}' (user={})", verb, proposerName, membership.getUser().getUsername());
            } else {
                log.warn("Motion {}: proposer '{}' not found in session {} members (available keys: {})",
                        verb, proposerName, session.getId(), membersByName.keySet());
            }
        } else {
            log.warn("Motion {}: proposer name is null/blank for motion id={}", verb, motionId);
        }
        if (!notifiedProposer) {
            notificationService.notifySessionDelegates(session,
                    "Motion " + verb,
                    "The chair has " + verb + " a motion" + (proposerName != null ? " by " + proposerName : "") + ".",
                    "MOTION",
                    "/delegate/motions");
            log.info("Motion {} notification broadcast to all delegates in session {} (fallback)", verb, session.getId());
        }
        result.put("success", true);
        result.put("status", status);
        result.put("message", "Motion " + verb + ".");
        liveEventService.publish("motion.changed", "");
        return result;
    }

    /**
     * Unified live-voting status used by the shared voting modal. Works for both
     * the chair (owned/active session) and delegates (joined session) so a single
     * poll powers live updates for everyone. Returns the live tally always.
     */
    @GetMapping("/api/voting/status")
    @ResponseBody
    public Map<String, Object> votingStatus(Principal principal) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (principal == null) {
            out.put("success", false);
            out.put("message", "Not authenticated.");
            return out;
        }
        String username = principal.getName();
        boolean chair = userRepository.findByUsername(username)
                .map(u -> UserService.isChairRole(u.getRole()))
                .orElse(false);

        Session session;
        String fullName = null;
        if (chair) {
            session = sessionService.getActiveSession().or(() -> sessionService.getLatestSession()).orElse(null);
        } else {
            session = delegateService.getJoinedSession(username).orElse(null);
            if (session != null) {
                fullName = delegateService.getDelegateFullName(username).orElse(username);
            }
        }
        if (session == null) {
            out.put("success", false);
            out.put("message", chair ? "No active session yet." : "Join a session first.");
            return out;
        }

        out.put("success", true);
        out.put("isChair", chair);
        out.put("sessionId", session.getId());
        out.put("ballotOpen", voteService.isBallotOpen(session));
        out.put("ballotType", session.getBallotType());
        out.put("ballotRefId", session.getBallotRefId());
        out.put("ballotLabel", ballotLabel(session));
        if (!chair) {
            out.put("myVote", voteService.getMyVote(session, fullName)
                    .map(com.ishan.sciverse.summit.entity.Vote::getChoice).orElse(null));
        }
        out.put("tally", voteService.tally(session));
        return out;
    }

    @PostMapping("/api/motions/status")
    @ResponseBody
    public String updateMotionStatus(@RequestParam Long id, @RequestParam String status) {
        motionService.updateStatus(id, status);
        Long sid = motionService.getById(id)
                .map(m -> m.getSession() != null ? m.getSession().getId() : null)
                .orElse(null);
        auditService.log("MOTION_STATUS_CHANGED", "MOTION", id, sid,
                "Motion status set to " + status + ".");
        liveEventService.publish("motion.changed", "");
        return "ok";
    }

    // ── CHAIR VOTING CONTROLS ──────────────────────────────────────────────

    @PostMapping("/api/voting/open")
    @ResponseBody
    public String openVoting(@RequestParam Long sessionId,
                             @RequestParam String ballotType,
                             @RequestParam Long ballotRefId) {
        // Voting is only available for resolutions — motions are decided by the chair.
        if (!"RESOLUTION".equalsIgnoreCase(ballotType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Motions are decided by the chair — voting is only available for resolutions.");
        }
        String displayTitle = resolutionService.getById(ballotRefId)
                .map(ResolutionDraft::getTitle).orElse("the resolution");
        voteService.openBallot(sessionId, "RESOLUTION", ballotRefId, displayTitle);
        liveEventService.publish("vote.changed", "");
        sessionService.getSessionById(sessionId).ifPresent(session -> {
            String title = "Voting is live";
            String message = "Voting is live for the " + ballotType.toLowerCase()
                    + " \"" + displayTitle + "\". Cast your vote now!";
            notificationService.notifySessionDelegates(session, title, message, "VOTING", "/delegate/voting");
        });
        return "ok";
    }

    @PostMapping("/api/voting/close")
    @ResponseBody
    public String closeVoting(@RequestParam Long sessionId) {
        voteService.closeBallot(sessionId);
        liveEventService.publish("vote.changed", "");
        return "ok";
    }

    @GetMapping("/api/voting/tally")
    @ResponseBody
    public Map<String, Object> votingTally(@RequestParam(required = false) Long sessionId) {
        Optional<Session> sessionOpt = sessionId != null
                ? sessionService.getOwnedSession(sessionId).or(() -> sessionService.getSessionById(sessionId))
                : sessionService.getActiveSession().or(() -> sessionService.getLatestSession());
        return sessionOpt.map(voteService::tally).orElseGet(LinkedHashMap::new);
    }

    // ── BALLOT HISTORY ──────────────────────────────────────────────────────

    @GetMapping("/api/ballot-history")
    @ResponseBody
    public List<Map<String, Object>> ballotHistory(@RequestParam(required = false) Long sessionId) {
        List<Map<String, Object>> out = new ArrayList<>();
        Optional<Session> sessionOpt = sessionId != null
                ? sessionService.getOwnedSession(sessionId).or(() -> sessionService.getSessionById(sessionId))
                : sessionService.getActiveSession().or(() -> sessionService.getLatestSession());
        if (sessionOpt.isEmpty()) {
            return out;
        }
        Session session = sessionOpt.get();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM HH:mm");
        for (com.ishan.sciverse.summit.entity.BallotRecord rec : ballotRecordService.listBySession(session)) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", rec.getId());
            o.put("ballotType", rec.getBallotType());
            o.put("ballotRefId", rec.getBallotRefId());
            o.put("title", rec.getTitle());
            o.put("status", rec.getStatus());
            o.put("result", rec.getResult());
            o.put("forCount", rec.getForCount());
            o.put("againstCount", rec.getAgainstCount());
            o.put("neutralCount", rec.getNeutralCount());
            o.put("totalVotes", rec.getTotalVotes());
            o.put("openedAt", rec.getOpenedAt() != null ? rec.getOpenedAt().format(fmt) : "");
            o.put("closedAt", rec.getClosedAt() != null ? rec.getClosedAt().format(fmt) : "");
            o.put("ballotRefIdLabel", rec.getBallotRefId());
            out.add(o);
        }
        return out;
    }

    // ── DELEGATE JOIN STATUS ────────────────────────────────────────────────

    @GetMapping("/api/delegate-memberships")
    @ResponseBody
    public List<Map<String, Object>> delegateMemberships(@RequestParam(required = false) Long sessionId) {
        List<Map<String, Object>> out = new ArrayList<>();
        Optional<Session> sessionOpt = sessionId != null
                ? sessionService.getOwnedSession(sessionId).or(() -> sessionService.getSessionById(sessionId))
                : sessionService.getActiveSession().or(() -> sessionService.getLatestSession());
        if (sessionOpt.isEmpty()) {
            return out;
        }
        Session session = sessionOpt.get();
        java.util.Map<String, com.ishan.sciverse.summit.entity.DelegateMembership> joinedMap =
                delegateService.getJoinedMembersByName(session);
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM HH:mm");
        for (com.ishan.sciverse.summit.data.Presentation p : presentationRepository.findBySessionOrderByIdAsc(session)) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("name", p.getName());
            com.ishan.sciverse.summit.entity.DelegateMembership m = joinedMap.get(
                    p.getName() != null ? p.getName().trim().toLowerCase() : "");
            o.put("joined", m != null);
            o.put("joinedAt", m != null && m.getJoinedAt() != null ? m.getJoinedAt().format(fmt) : null);
            out.add(o);
        }
        return out;
    }

    // ── CHAIR RESOLUTIONS ──────────────────────────────────────────────────

    /** All resolutions uploaded to the chair's active/latest session (for the chair's Resolution page list). */
    @GetMapping("/api/resolutions")
    @ResponseBody
    public List<Map<String, Object>> chairResolutions(@RequestParam(required = false) Long sessionId) {        List<Map<String, Object>> out = new ArrayList<>();
        Optional<Session> sessionOpt = sessionId != null
                ? sessionService.getOwnedSession(sessionId).or(() -> sessionService.getSessionById(sessionId))
                : sessionService.getActiveSession().or(() -> sessionService.getLatestSession());
        if (sessionOpt.isEmpty()) {
            return out;
        }
        Session session = sessionOpt.get();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM HH:mm");
        for (ResolutionDraft d : resolutionService.listBySession(session)) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", d.getId());
            o.put("title", d.getTitle());
            o.put("primaryAuthor", d.getSubmitter());
            o.put("primaryAuthor2", d.getPrimaryAuthor2());
            o.put("sponsors", d.getSponsors());
            o.put("time", d.getUploadedAt() != null ? d.getUploadedAt().format(fmt) : "");
            o.put("hasFile", d.getFilePath() != null);
            o.put("fileCount", d.getFiles() == null ? (d.getFilePath() != null ? 1 : 0) : d.getFiles().size());
            List<String> types = new ArrayList<>();
            if (d.getFiles() != null) {
                for (com.ishan.sciverse.summit.entity.ResolutionDraftFile f : d.getFiles()) {
                    if (f.getFileType() != null && !types.contains(f.getFileType())) {
                        types.add(f.getFileType());
                    }
                }
            }
            if (types.isEmpty() && d.getFilePath() != null) {
                types.add("FILE");
            }
            o.put("fileTypes", types);
            boolean ballotOn = voteService.isBallotOpen(session)
                    && "RESOLUTION".equalsIgnoreCase(session.getBallotType())
                    && d.getId().equals(session.getBallotRefId());
            o.put("votingOpen", ballotOn);
            Map<String, Object> outcome = resolutionVotingOutcome(session, d.getId());
            o.put("votingDone", outcome != null);
            o.put("votingOutcome", outcome != null ? outcome.get("result") : null);
            o.put("votingClosedAt", outcome != null ? outcome.get("closedAt") : null);
            out.add(o);
        }
        return out;
    }

    /** Full detail of one resolution for the chair's Resolution page detail panel. */
    @GetMapping("/api/resolutions/{id}")
    @ResponseBody
    public Map<String, Object> chairResolutionDetail(@org.springframework.web.bind.annotation.PathVariable Long id) {
        Map<String, Object> out = new LinkedHashMap<>();
        ResolutionDraft d = resolutionService.getById(id).orElse(null);
        if (d == null || d.getSession() == null) {
            out.put("success", false);
            out.put("message", "Resolution not found.");
            return out;
        }
        Session session = d.getSession();
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
        out.put("success", true);
        out.put("id", d.getId());
        out.put("sessionId", session.getId());
        out.put("title", d.getTitle());
        out.put("primaryAuthor", d.getSubmitter());
        out.put("primaryAuthor2", d.getPrimaryAuthor2());
        out.put("sponsors", d.getSponsors());
        out.put("description", d.getDescription());
        out.put("operativeClauses", d.getOperativeClauses());
        out.put("fileName", d.getFileName());
        out.put("hasFile", d.getFilePath() != null);
        out.put("files", resolutionFiles(d));
        out.put("time", d.getUploadedAt() != null ? d.getUploadedAt().format(fmt) : "");
        boolean ballotOn = voteService.isBallotOpen(session)
                && "RESOLUTION".equalsIgnoreCase(session.getBallotType())
                && d.getId().equals(session.getBallotRefId());
        out.put("votingOpen", ballotOn);
        out.put("ballotOpen", voteService.isBallotOpen(session));
        Map<String, Object> outcome = resolutionVotingOutcome(session, d.getId());
        out.put("votingDone", outcome != null);
        out.put("votingOutcome", outcome != null ? outcome.get("result") : null);
        out.put("votingClosedAt", outcome != null ? outcome.get("closedAt") : null);
        return out;
    }

    /**
     * Latest CLOSED resolution ballot for a resolution, if any, with the outcome
     * recomputed under the rule "majority FOR = pass" (pass iff FOR strictly beats
     * AGAINST + NEUTRAL). Returns null when no ballot has finished for it.
     */
    private Map<String, Object> resolutionVotingOutcome(Session session, Long resolutionId) {
        java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("dd MMM HH:mm");
        for (com.ishan.sciverse.summit.entity.BallotRecord rec : ballotRecordService.listBySession(session)) {
            if ("RESOLUTION".equalsIgnoreCase(rec.getBallotType())
                    && resolutionId.equals(rec.getBallotRefId())
                    && "CLOSED".equalsIgnoreCase(rec.getStatus())) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("result", (rec.getForCount() > rec.getAgainstCount() + rec.getNeutralCount())
                        ? "PASSED" : "FAILED");
                out.put("for", rec.getForCount());
                out.put("against", rec.getAgainstCount());
                out.put("neutral", rec.getNeutralCount());
                out.put("total", rec.getTotalVotes());
                out.put("closedAt", rec.getClosedAt() != null ? rec.getClosedAt().format(fmt) : "");
                return out;
            }
        }
        return null;
    }

    /** Maps every uploaded draft document of a resolution to a small JSON-safe descriptor. */
    private List<Map<String, Object>> resolutionFiles(ResolutionDraft d) {
        List<Map<String, Object>> files = new ArrayList<>();
        if (d.getFiles() != null) {
            for (com.ishan.sciverse.summit.entity.ResolutionDraftFile f : d.getFiles()) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("id", f.getId());
                o.put("fileName", f.getFileName());
                o.put("fileType", f.getFileType());
                files.add(o);
            }
        }
        if (files.isEmpty() && d.getFilePath() != null) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", null);
            o.put("fileName", d.getFileName());
            o.put("fileType", "FILE");
            files.add(o);
        }
        return files;
    }

    /** Extracted text preview of an uploaded resolution document for the chair's detail panel. */
    @GetMapping("/api/resolutions/{id}/preview")
    @ResponseBody
    public Map<String, Object> chairResolutionPreview(@org.springframework.web.bind.annotation.PathVariable Long id) {
        Map<String, Object> out = new LinkedHashMap<>();
        ResolutionDraft d = resolutionService.getById(id).orElse(null);
        if (d == null) {
            out.put("success", false);
            out.put("message", "No file attached.");
            return out;
        }
        Path path = null;
        String fileName = d.getFileName();
        if (d.getFiles() != null && !d.getFiles().isEmpty()) {
            // Prefer the PDF version when available, otherwise the Word version.
            com.ishan.sciverse.summit.entity.ResolutionDraftFile preferred = d.getFiles().stream()
                    .filter(f -> "PDF".equalsIgnoreCase(f.getFileType()))
                    .findFirst()
                    .orElse(d.getFiles().get(0));
            path = Paths.get(preferred.getFilePath());
            fileName = preferred.getFileName();
        } else if (d.getFilePath() != null) {
            path = Paths.get(d.getFilePath());
        }
        if (path == null) {
            out.put("success", false);
            out.put("message", "No file attached.");
            return out;
        }
        if (!Files.exists(path)) {
            out.put("success", false);
            out.put("message", "File is missing on the server.");
            return out;
        }
        String text = resolutionFilePreviewService.extractText(path, fileName);
        if (text == null || text.isBlank()) {
            out.put("success", false);
            out.put("message", "Preview unavailable for this file type.");
            return out;
        }
        out.put("success", true);
        out.put("fileName", fileName);
        out.put("previewText", text);
        return out;
    }

    // ── RESOLUTION FILE SERVING ────────────────────────────────────────────

    /** Serves one uploaded draft document (a specific PDF or Word version) by file id. */
    @GetMapping("/api/resolution/file/entry/{fileId}")
    @ResponseBody
    public ResponseEntity<Resource> resolutionFileEntry(@org.springframework.web.bind.annotation.PathVariable Long fileId) {
        Optional<com.ishan.sciverse.summit.entity.ResolutionDraftFile> entryOpt =
                resolutionDraftFileRepository.findById(fileId);
        if (entryOpt.isEmpty() || entryOpt.get().getFilePath() == null) {
            return ResponseEntity.notFound().build();
        }
        com.ishan.sciverse.summit.entity.ResolutionDraftFile entry = entryOpt.get();
        Path path = Paths.get(entry.getFilePath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(path.toAbsolutePath());
        String contentType = "application/octet-stream";
        try {
            contentType = Files.probeContentType(path);
        } catch (Exception ignored) {
            // fall back to octet-stream
        }
        if (contentType == null) contentType = "application/octet-stream";
        String fileName = entry.getFileName() != null ? entry.getFileName() : "resolution";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + fileName.replace("\"", "") + "\"")
                .body(resource);
    }

    @GetMapping("/api/resolution/file/{id}")
    @ResponseBody
    public ResponseEntity<Resource> resolutionFile(@org.springframework.web.bind.annotation.PathVariable Long id) {
        Optional<ResolutionDraft> draftOpt = resolutionService.getById(id);
        if (draftOpt.isEmpty() || draftOpt.get().getFilePath() == null) {
            return ResponseEntity.notFound().build();
        }
        Path path = Paths.get(draftOpt.get().getFilePath());
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new FileSystemResource(path.toAbsolutePath());
        String contentType = "application/octet-stream";
        try {
            contentType = Files.probeContentType(path);
        } catch (Exception ignored) {
            // fall back to octet-stream
        }
        if (contentType == null) contentType = "application/octet-stream";
        String fileName = draftOpt.get().getFileName() != null ? draftOpt.get().getFileName() : "resolution";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + fileName.replace("\"", "") + "\"")
                .body(resource);
    }
}
