package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.entity.Vote;
import com.ishan.sciverse.summit.repository.SessionRepository;
import com.ishan.sciverse.summit.repository.VoteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class VoteService {

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private BallotRecordService ballotRecordService;

    @Autowired
    private AuditService auditService;

    public void openBallot(Long sessionId, String ballotType, Long ballotRefId, String title) {
        sessionRepository.findById(sessionId).ifPresent(s -> {
            s.setBallotOpen(true);
            s.setBallotType(ballotType);
            s.setBallotRefId(ballotRefId);
            sessionRepository.save(s);
            ballotRecordService.recordOpen(s, ballotType, ballotRefId, title);
            auditService.log("BALLOT_OPENED", "BALLOT", ballotRefId, s.getId(),
                    "Opened " + ballotType + " ballot for \"" + title + "\".");
        });
    }

    public void closeBallot(Long sessionId) {
        sessionRepository.findById(sessionId).ifPresent(s -> {
            String type = s.getBallotType();
            Long refId = s.getBallotRefId();
            if (type != null && refId != null) {
                Map<String, Object> t = tally(s, type, refId);
                ballotRecordService.recordClose(s, type, refId,
                        ((Number) t.getOrDefault("for", 0)).intValue(),
                        ((Number) t.getOrDefault("against", 0)).intValue(),
                        ((Number) t.getOrDefault("neutral", 0)).intValue());
            }
            s.setBallotOpen(false);
            sessionRepository.save(s);
            auditService.log("BALLOT_CLOSED", "BALLOT", s.getBallotRefId(), s.getId(),
                    "Closed " + s.getBallotType() + " ballot.");
        });
    }

    /** True when the session has an open ballot. */
    public boolean isBallotOpen(Session session) {
        return Boolean.TRUE.equals(session.getBallotOpen());
    }

    /** Casts (or updates) the delegate's vote. Returns the current tally. */
    @Transactional
    public Map<String, Object> castVote(Session session, String delegateName, String choice) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (!isBallotOpen(session) || session.getBallotType() == null) {
            result.put("success", false);
            result.put("message", "Voting is not open for this session.");
            auditService.log("VOTE_REJECTED", "BALLOT", session.getBallotRefId(), session.getId(),
                    delegateName + " tried to vote while no ballot is open.");
            return result;
        }
        if (choice == null || choice.isBlank()) {
            result.put("success", false);
            result.put("message", "Please choose an option before submitting.");
            auditService.log("VOTE_REJECTED", "BALLOT", session.getBallotRefId(), session.getId(),
                    delegateName + " submitted an empty vote.");
            return result;
        }
        String normalizedChoice = choice.trim().toUpperCase();
        if ("ABSTAIN".equals(normalizedChoice)) {
            normalizedChoice = "NEUTRAL"; // ABSTAIN is kept as an alias for older clients
        }
        if (!List.of("FOR", "AGAINST", "NEUTRAL").contains(normalizedChoice)) {
            result.put("success", false);
            result.put("message", "Invalid choice.");
            auditService.log("VOTE_REJECTED", "BALLOT", session.getBallotRefId(), session.getId(),
                    delegateName + " submitted invalid choice \"" + choice + "\".");
            return result;
        }

        String type = session.getBallotType();
        Long refId = session.getBallotRefId();

        Vote vote = voteRepository
                .findBySessionAndBallotTypeAndBallotRefIdAndDelegateName(session, type, refId, delegateName)
                .orElseGet(() -> {
                    Vote v = new Vote();
                    v.setSession(session);
                    v.setBallotType(type);
                    v.setBallotRefId(refId);
                    v.setDelegateName(delegateName);
                    return v;
                });
        vote.setChoice(normalizedChoice);
        voteRepository.save(vote);
        auditService.log("VOTE_CAST", "BALLOT", refId, session.getId(),
                delegateName + " voted " + normalizedChoice + " on " + type + " ballot.");

        result.put("success", true);
        result.put("message", "Your vote has been recorded.");
        result.put("tally", tally(session, type, refId));
        return result;
    }

    public Optional<Vote> getMyVote(Session session, String delegateName) {
        if (session.getBallotType() == null) return Optional.empty();
        return voteRepository.findBySessionAndBallotTypeAndBallotRefIdAndDelegateName(
                session, session.getBallotType(), session.getBallotRefId(), delegateName);
    }

    public Optional<Vote> getVoteForBallot(Session session, String delegateName, String ballotType, Long ballotRefId) {
        return voteRepository.findBySessionAndBallotTypeAndBallotRefIdAndDelegateName(
                session, ballotType, ballotRefId, delegateName);
    }

    /** Counts FOR / AGAINST / NEUTRAL (ABSTAIN from older ballots counts as NEUTRAL) for the session's open ballot. */
    public Map<String, Object> tally(Session session) {
        if (session.getBallotType() == null) {
            return tally(session, null, null);
        }
        return tally(session, session.getBallotType(), session.getBallotRefId());
    }

    private Map<String, Object> tally(Session session, String ballotType, Long ballotRefId) {
        List<Vote> votes = (ballotType == null)
                ? voteRepository.findBySession(session)
                : voteRepository.findBySessionAndBallotTypeAndBallotRefId(session, ballotType, ballotRefId);

        long forCount = votes.stream().filter(v -> "FOR".equals(v.getChoice())).count();
        long againstCount = votes.stream().filter(v -> "AGAINST".equals(v.getChoice())).count();
        long neutralCount = votes.stream()
                .filter(v -> "NEUTRAL".equals(v.getChoice()) || "ABSTAIN".equals(v.getChoice()))
                .count();

        Map<String, Object> tally = new LinkedHashMap<>();
        tally.put("for", forCount);
        tally.put("against", againstCount);
        tally.put("neutral", neutralCount);
        tally.put("total", votes.size());
        return tally;
    }
}
