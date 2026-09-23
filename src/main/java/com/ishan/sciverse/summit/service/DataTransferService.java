package com.ishan.sciverse.summit.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ishan.sciverse.summit.data.Presentation;
import com.ishan.sciverse.summit.dto.ImportDelegate;
import com.ishan.sciverse.summit.dto.ImportFile;
import com.ishan.sciverse.summit.dto.ImportSession;
import com.ishan.sciverse.summit.dto.ImportUser;
import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.entity.User;
import com.ishan.sciverse.summit.repository.PresentationRepository;
import com.ishan.sciverse.summit.repository.SessionRepository;
import com.ishan.sciverse.summit.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DataTransferService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private PresentationRepository presentationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    // ── EXPORT ────────────────────────────────────────────────────────────

    public ImportFile buildExport() {
        ImportFile file = new ImportFile();
        file.setApp("Sciverse Summit");
        file.setExportVersion(1);
        file.setExportedAt(java.time.LocalDateTime.now().toString());
        List<ImportUser> users = new ArrayList<>();
        for (User u : userRepository.findAll()) {
            users.add(toImportUser(u));
        }
        file.setUsers(users);
        return file;
    }

    private ImportUser toImportUser(User u) {
        ImportUser iu = new ImportUser();
        iu.setFullName(u.getFullName());
        iu.setUsername(u.getUsername());
        iu.setEmail(u.getEmail());
        iu.setPhoneNumber(u.getPhoneNumber());
        iu.setGender(u.getGender());
        iu.setRole(u.getRole());
        iu.setPassword(u.getPassword());
        iu.setRawPassword(u.getRawPassword());

        List<ImportSession> sessions = new ArrayList<>();
        for (Session s : sessionRepository.findByUser(u)) {
            sessions.add(toImportSession(s));
        }
        iu.setSessions(sessions);
        return iu;
    }

    private ImportSession toImportSession(Session s) {
        ImportSession is = new ImportSession();
        is.setName(s.getName());
        is.setCommittee(s.getCommittee());
        is.setStrength(s.getStrength());
        is.setTopic(s.getTopic());
        is.setCreatedAt(s.getCreatedAt());
        is.setActive(Boolean.TRUE.equals(s.getActive()) ? Boolean.TRUE : Boolean.FALSE);
        is.setNotes(s.getNotes());
        is.setEbReview(s.getEbReview());
        is.setJoinCode(s.getJoinCode());

        List<ImportDelegate> delegates = new ArrayList<>();
        for (Presentation p : presentationRepository.findBySession(s)) {
            delegates.add(toImportDelegate(p));
        }
        is.setDelegates(delegates);
        return is;
    }

    private ImportDelegate toImportDelegate(Presentation p) {
        ImportDelegate d = new ImportDelegate();
        d.setName(p.getName());
        d.setPresenting(p.isPresenting());
        d.setVoting(p.isVoting());
        d.setTimesSpoken(p.getTimesSpoken());
        d.setTotalSpeakingTime(p.getTotalSpeakingTime());
        d.setMotionProposals(p.getMotionProposals());
        d.setAmendmentProposals(p.getAmendmentProposals());
        d.setSciKnowledge(p.getSciKnowledge());
        d.setRepresentationAccuracy(p.getRepresentationAccuracy());
        d.setPublicSpeaking(p.getPublicSpeaking());
        d.setParticipation(p.getParticipation());
        d.setResolutionDrafting(p.getResolutionDrafting());
        d.setCollaboration(p.getCollaboration());
        d.setLeadershipMatrix(p.getLeadershipMatrix());
        return d;
    }

    public String exportJson() throws JsonProcessingException {
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(buildExport());
    }

    // ── IMPORT ────────────────────────────────────────────────────────────

    public ImportFile parse(byte[] content) throws Exception {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("The selected file is empty.");
        }
        ImportFile file = objectMapper.readValue(content, ImportFile.class);
        if (file.getUsers() == null) file.setUsers(new ArrayList<>());
        for (ImportUser u : file.getUsers()) {
            if (u.getSessions() == null) u.setSessions(new ArrayList<>());
            for (ImportSession s : u.getSessions()) {
                if (s.getDelegates() == null) s.setDelegates(new ArrayList<>());
            }
        }
        return file;
    }

    /**
     * Counts sessions and delegates carried by a single imported user.
     */
    public int[] countForUser(ImportUser u) {
        int sessions = u.getSessions() == null ? 0 : u.getSessions().size();
        int delegates = 0;
        if (u.getSessions() != null) {
            for (ImportSession s : u.getSessions()) {
                delegates += s.getDelegates() == null ? 0 : s.getDelegates().size();
            }
        }
        return new int[] { sessions, delegates };
    }

    /**
     * Imports only the users whose username is present in {@code selectedUsernames}.
     * Users whose username or email already exists are skipped.
     */
    @Transactional
    public List<Map<String, Object>> importUsers(ImportFile file, Set<String> selectedUsernames) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (ImportUser iu : file.getUsers()) {
            if (iu.getUsername() == null || iu.getUsername().isBlank()) {
                results.add(userResult(iu, "skipped", "The user has no username and was ignored."));
                continue;
            }
            if (!selectedUsernames.contains(iu.getUsername())) {
                continue;
            }
            if (userRepository.findByUsername(iu.getUsername()).isPresent()) {
                results.add(userResult(iu, "skipped",
                        "Username '" + iu.getUsername() + "' already exists in the database."));
                continue;
            }
            if (iu.getEmail() != null && !iu.getEmail().isBlank()
                    && userRepository.findByEmail(iu.getEmail()).isPresent()) {
                results.add(userResult(iu, "skipped",
                        "Email '" + iu.getEmail() + "' already exists in the database."));
                continue;
            }

            User user = new User();
            user.setFullName(nvl(iu.getFullName()));
            user.setUsername(iu.getUsername().trim());
            user.setEmail(nvl(iu.getEmail()));
            user.setPhoneNumber(nvl(iu.getPhoneNumber()));
            user.setGender(nvl(iu.getGender()));
            user.setRole(iu.getRole() != null && !iu.getRole().isBlank() ? iu.getRole().trim() : "USER");
            user.setPassword(resolvePassword(iu));
            user.setRawPassword(nvl(iu.getRawPassword()));
            userRepository.save(user);

            int sCount = 0, dCount = 0;
            for (ImportSession is : iu.getSessions()) {
                if (is.getName() == null || is.getName().isBlank()) continue;
                Session session = new Session();
                session.setName(is.getName().trim());
                session.setCommittee(is.getCommittee() != null && !is.getCommittee().isBlank()
                        ? is.getCommittee().trim() : "General Assembly");
                session.setStrength(Math.max(0, is.getStrength()));
                session.setTopic(nvl(is.getTopic()));
                session.setActive(is.getActive() == null ? Boolean.TRUE : is.getActive());
                session.setNotes(is.getNotes());
                session.setEbReview(is.getEbReview());
                session.setCreatedAt(is.getCreatedAt());
                session.setUser(user);
                session.setJoinCode(resolveJoinCode(is.getJoinCode()));
                sessionRepository.save(session);
                sCount++;

                for (ImportDelegate d : is.getDelegates()) {
                    if (d.getName() == null || d.getName().isBlank()) continue;
                    Presentation p = new Presentation();
                    p.setName(d.getName().trim());
                    p.setPresenting(d.isPresenting());
                    p.setVoting(d.isVoting());
                    p.setTimesSpoken(d.getTimesSpoken());
                    p.setTotalSpeakingTime(d.getTotalSpeakingTime());
                    p.setMotionProposals(d.getMotionProposals());
                    p.setAmendmentProposals(d.getAmendmentProposals());
                    p.setSciKnowledge(d.getSciKnowledge());
                    p.setRepresentationAccuracy(d.getRepresentationAccuracy());
                    p.setPublicSpeaking(d.getPublicSpeaking());
                    p.setParticipation(d.getParticipation());
                    p.setResolutionDrafting(d.getResolutionDrafting());
                    p.setCollaboration(d.getCollaboration());
                    p.setLeadershipMatrix(d.getLeadershipMatrix());
                    p.setSession(session);
                    presentationRepository.save(p);
                    dCount++;
                }
            }

            results.add(userResult(iu, "imported",
                    sCount + " session(s) and " + dCount + " delegate(s) imported."));
        }
        return results;
    }

    private String resolvePassword(ImportUser iu) {
        if (iu.getPassword() != null && !iu.getPassword().isBlank()) {
            return iu.getPassword();
        }
        if (iu.getRawPassword() != null && !iu.getRawPassword().isBlank()) {
            return passwordEncoder.encode(iu.getRawPassword());
        }
        return passwordEncoder.encode("sciverse@123");
    }

    private String resolveJoinCode(String existing) {
        if (existing != null && !existing.isBlank()) {
            return existing.trim().toUpperCase();
        }
        String code;
        do {
            code = SessionService.generateJoinCode();
        } while (sessionRepository.findByJoinCodeIgnoreCase(code).isPresent());
        return code;
    }

    private Map<String, Object> userResult(ImportUser iu, String status, String message) {
        int[] counts = countForUser(iu);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fullName", iu.getFullName());
        m.put("username", iu.getUsername());
        m.put("email", iu.getEmail());
        m.put("status", status);
        m.put("sessions", counts[0]);
        m.put("delegates", counts[1]);
        m.put("message", message);
        return m;
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
