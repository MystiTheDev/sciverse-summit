package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.service.DelegateService;
import com.ishan.sciverse.summit.service.LiveEventService;
import com.ishan.sciverse.summit.service.MotionService;
import com.ishan.sciverse.summit.service.NotificationService;
import com.ishan.sciverse.summit.service.ResolutionService;
import com.ishan.sciverse.summit.service.VoteService;
import com.ishan.sciverse.summit.entity.Motion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
public class DelegateApiController {

    private static final Logger log = LoggerFactory.getLogger(DelegateApiController.class);

    @Autowired
    private DelegateService delegateService;

    @Autowired
    private MotionService motionService;

    @Autowired
    private ResolutionService resolutionService;

    @Autowired
    private VoteService voteService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private LiveEventService liveEventService;

    @Autowired
    private com.ishan.sciverse.summit.service.BallotRecordService ballotRecordService;

    @Autowired
    private com.ishan.sciverse.summit.service.AuditService auditService;

    @PostMapping("/api/delegate/join")
    @ResponseBody
    public Map<String, Object> join(@RequestParam String code, Principal principal) {
        if (principal == null) {
            return error("Not authenticated.");
        }
        Map<String, Object> result = delegateService.joinSession(principal.getName(), code);
        if (Boolean.TRUE.equals(result.get("success"))) {
            liveEventService.publish("delegate.changed", "");
        }
        return result;
    }

    @GetMapping("/api/delegate/motions")
    @ResponseBody
    public List<Map<String, Object>> myMotions(Principal principal) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (principal == null) {
            return out;
        }
        Session session = joinedOrNull(principal);
        if (session == null) {
            return out;
        }
        String fullName = delegateService.getDelegateFullName(principal.getName()).orElse(principal.getName());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        for (Motion m : motionService.listBySession(session)) {
            if (m.getProposer() != null && m.getProposer().equalsIgnoreCase(fullName)) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("id", m.getId());
                o.put("name", m.getType());
                o.put("detail", m.getDetail());
                o.put("time", m.getRaisedAt() != null ? m.getRaisedAt().format(fmt) : "");
                o.put("status", m.getStatus());
                out.add(o);
            }
        }
        return out;
    }

    @GetMapping("/api/delegate/motions/live")
    @ResponseBody
    public List<Map<String, Object>> liveMotions(Principal principal) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (principal == null) {
            return out;
        }
        Session session = joinedOrNull(principal);
        if (session == null) {
            return out;
        }
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
        for (Motion m : motionService.listLiveBySession(session)) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", m.getId());
            o.put("name", m.getType());
            o.put("detail", m.getDetail());
            o.put("proposer", m.getProposer());
            o.put("time", m.getRaisedAt() != null ? m.getRaisedAt().format(fmt) : "");
            o.put("status", m.getStatus());
            out.add(o);
        }
        return out;
    }

    @PostMapping("/api/delegate/motion/raise")
    @ResponseBody
    public Map<String, Object> raiseMotion(@RequestParam String type,
                                           @RequestParam(required = false) String detail,
                                           Principal principal) {
        if (principal == null) {
            return error("Not authenticated.");
        }
        Session session = joinedOrNull(principal);
        if (session == null) {
            return error("Join a session first.");
        }
        String fullName = delegateService.getDelegateFullName(principal.getName()).orElse(principal.getName());
        delegateService.incrementProposalForUser(session, principal.getName(), false);
        String cleanDetail = detail != null && !detail.isBlank() ? detail.trim() : null;
        if (cleanDetail != null && cleanDetail.length() > 500) {
            cleanDetail = cleanDetail.substring(0, 500);
        }
        Motion raised = motionService.raise(session, type, cleanDetail, fullName);
        auditService.log("MOTION_RAISED", "MOTION", raised.getId(), session.getId(),
                fullName + " raised motion \"" + type + "\".");
        liveEventService.publish("motion.changed", "");
        if (session.getUser() != null) {
            notificationService.notifyUser(
                    session.getUser(),
                    "New Motion",
                    fullName + " has raised a motion: \"" + type + "\".",
                    "MOTION",
                    "/motions");
        } else {
            log.warn("Cannot notify chair: session.getUser() is null for session={}", session.getId());
        }
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("success", true);
        ok.put("message", "Motion \"" + type + "\" raised. The chair can now see it live.");
        return ok;
    }

    @PostMapping("/api/delegate/motion/withdraw")
    @ResponseBody
    public Map<String, Object> withdrawMotion(@RequestParam Long motionId, Principal principal) {
        if (principal == null) {
            return error("Not authenticated.");
        }
        Session session = joinedOrNull(principal);
        if (session == null) {
            return error("Join a session first.");
        }
        String fullName = delegateService.getDelegateFullName(principal.getName()).orElse(principal.getName());
        var motionOpt = motionService.getById(motionId);
        if (motionOpt.isEmpty() || motionOpt.get().getSession() == null
                || !motionOpt.get().getSession().getId().equals(session.getId())) {
            return error("Motion not found.");
        }
        Motion m = motionOpt.get();
        if (m.getProposer() == null || !m.getProposer().equalsIgnoreCase(fullName)) {
            return error("You can only withdraw your own motion.");
        }
        if (!"PENDING".equalsIgnoreCase(m.getStatus())) {
            return error("Only pending motions can be withdrawn.");
        }
        motionService.updateStatus(motionId, "WITHDRAWN");
        auditService.log("MOTION_WITHDRAWN", "MOTION", motionId, session.getId(),
                fullName + " withdrew motion \"" + m.getType() + "\".");
        liveEventService.publish("motion.changed", "");
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("success", true);
        ok.put("message", "Motion withdrawn.");
        return ok;
    }

    @GetMapping("/api/delegate/resolutions")
    @ResponseBody
    public List<Map<String, Object>> myResolutions(Principal principal) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (principal == null) {
            return out;
        }
        Session session = joinedOrNull(principal);
        if (session == null) {
            return out;
        }
        String fullName = delegateService.getDelegateFullName(principal.getName()).orElse(principal.getName());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM HH:mm");
        for (com.ishan.sciverse.summit.entity.ResolutionDraft d : resolutionService.listBySession(session)) {
            if (d.getSubmitter() != null && d.getSubmitter().equalsIgnoreCase(fullName)) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("id", d.getId());
                o.put("title", d.getTitle());
                o.put("primaryAuthor", d.getSubmitter());
                o.put("primaryAuthor2", d.getPrimaryAuthor2());
                o.put("sponsors", d.getSponsors());
                o.put("description", d.getDescription());
                o.put("operativeClauses", d.getOperativeClauses());
                o.put("fileName", d.getFileName());
                o.put("time", d.getUploadedAt() != null ? d.getUploadedAt().format(fmt) : "");
                o.put("hasFile", d.getFilePath() != null);
                o.put("files", resolutionFiles(d));
                out.add(o);
            }
        }
        return out;
    }

    /** Maps every uploaded draft document of a resolution to a small JSON-safe descriptor. */
    private List<Map<String, Object>> resolutionFiles(com.ishan.sciverse.summit.entity.ResolutionDraft d) {
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

    @PostMapping("/api/delegate/resolution/submit")
    @ResponseBody
    public Map<String, Object> submitResolution(@RequestParam String title,
                                                @RequestParam(required = false) String coAuthor,
                                                @RequestParam(required = false) String sponsors,
                                                @RequestParam(required = false) String description,
                                                @RequestParam(required = false) String operativeClauses,
                                                @RequestParam(value = "file", required = false) MultipartFile[] files,
                                                Principal principal) {
        if (principal == null) {
            return error("Not authenticated.");
        }
        Session session = joinedOrNull(principal);
        if (session == null) {
            return error("Join a session first.");
        }
        if (title == null || title.isBlank()) {
            return error("Please provide a resolution title.");
        }
        try {
            String fullName = delegateService.getDelegateFullName(principal.getName()).orElse(principal.getName());
            resolutionService.submit(session, title, coAuthor, sponsors, description, operativeClauses, fullName, files);
            liveEventService.publish("motion.changed", "");
            if (session.getUser() != null) {
                notificationService.notifyUser(
                        session.getUser(),
                        "New Resolution",
                        fullName + " has submitted a resolution: \"" + title + "\".",
                        "RESOLUTION",
                        "/resolution");
            } else {
                log.warn("Cannot notify chair for resolution: session.getUser() is null for session={}", session.getId());
            }
            Map<String, Object> ok = new LinkedHashMap<>();
            ok.put("success", true);
            ok.put("message", "Draft resolution submitted.");
            return ok;
        } catch (Exception ex) {
            log.error("Resolution submit failed: {}", ex.getMessage(), ex);
            return error("Could not submit the resolution: " + ex.getMessage());
        }
    }

    @GetMapping("/api/delegate/voting/status")
    @ResponseBody
    public Map<String, Object> votingStatus(Principal principal) {
        if (principal == null) {
            return error("Not authenticated.");
        }
        Session session = joinedOrNull(principal);
        if (session == null) {
            return error("Join a session first.");
        }
        String fullName = delegateService.getDelegateFullName(principal.getName()).orElse(principal.getName());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", true);
        out.put("ballotOpen", voteService.isBallotOpen(session));
        out.put("ballotType", session.getBallotType());
        out.put("ballotRefId", session.getBallotRefId());
        out.put("ballotLabel", ballotLabel(session));
        out.put("myVote", voteService.getMyVote(session, fullName)
                .map(com.ishan.sciverse.summit.entity.Vote::getChoice).orElse(null));
        out.put("tally", voteService.tally(session));
        return out;
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

    @PostMapping("/api/delegate/vote")
    @ResponseBody
    public Map<String, Object> vote(@RequestParam String choice, Principal principal) {
        if (principal == null) {
            return error("Not authenticated.");
        }
        Session session = joinedOrNull(principal);
        if (session == null) {
            return error("Join a session first.");
        }
        String fullName = delegateService.getDelegateFullName(principal.getName()).orElse(principal.getName());
        Map<String, Object> result = voteService.castVote(session, fullName, choice);
        if (Boolean.TRUE.equals(result.get("success"))) {
            liveEventService.publish("vote.changed", "");
        }
        return result;
    }

    @PostMapping("/api/delegate/leave")
    @ResponseBody
    public Map<String, Object> leave(Principal principal) {
        if (principal == null) {
            return error("Not authenticated.");
        }
        Map<String, Object> result = delegateService.leaveSession(principal.getName());
        if (Boolean.TRUE.equals(result.get("success"))) {
            liveEventService.publish("delegate.changed", "");
        }
        return result;
    }

    @GetMapping("/api/delegate/ballot-history")
    @ResponseBody
    public List<Map<String, Object>> delegateBallotHistory(Principal principal) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (principal == null) {
            return out;
        }
        Session session = joinedOrNull(principal);
        if (session == null) {
            return out;
        }
        String fullName = delegateService.getDelegateFullName(principal.getName()).orElse(principal.getName());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM HH:mm");
        for (com.ishan.sciverse.summit.entity.BallotRecord rec : ballotRecordService.listBySession(session)) {
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", rec.getId());
            o.put("ballotType", rec.getBallotType());
            o.put("title", rec.getTitle());
            o.put("status", rec.getStatus());
            o.put("result", rec.getResult());
            o.put("forCount", rec.getForCount());
            o.put("againstCount", rec.getAgainstCount());
            o.put("neutralCount", rec.getNeutralCount());
            o.put("totalVotes", rec.getTotalVotes());
            o.put("openedAt", rec.getOpenedAt() != null ? rec.getOpenedAt().format(fmt) : "");
            o.put("closedAt", rec.getClosedAt() != null ? rec.getClosedAt().format(fmt) : "");
            com.ishan.sciverse.summit.entity.Vote myVote = voteService.getVoteForBallot(session, fullName, rec.getBallotType(), rec.getBallotRefId())
                    .orElse(null);
            o.put("myVote", myVote != null ? myVote.getChoice() : null);
            out.add(o);
        }
        return out;
    }

    private Session joinedOrNull(Principal principal) {
        return delegateService.getJoinedSession(principal.getName()).orElse(null);
    }

    private Map<String, Object> error(String message) {
        Map<String, Object> err = new LinkedHashMap<>();
        err.put("success", false);
        err.put("message", message);
        return err;
    }
}
