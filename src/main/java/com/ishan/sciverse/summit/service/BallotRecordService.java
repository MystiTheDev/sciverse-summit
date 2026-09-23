package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.entity.BallotRecord;
import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.repository.BallotRecordRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class BallotRecordService {

    @Autowired
    private BallotRecordRepository ballotRecordRepository;

    public BallotRecord recordOpen(Session session, String ballotType, Long ballotRefId, String title) {
        BallotRecord rec = new BallotRecord();
        rec.setSession(session);
        rec.setBallotType(ballotType);
        rec.setBallotRefId(ballotRefId);
        rec.setTitle(title);
        rec.setStatus("OPEN");
        return ballotRecordRepository.save(rec);
    }

    public void recordClose(Session session, String ballotType, Long ballotRefId,
                            int forCount, int againstCount, int neutralCount) {
        ballotRecordRepository
                .findFirstBySessionAndBallotTypeAndBallotRefIdAndStatus(session, ballotType, ballotRefId, "OPEN")
                .ifPresent(rec -> {
                    rec.setStatus("CLOSED");
                    rec.setForCount(forCount);
                    rec.setAgainstCount(againstCount);
                    rec.setNeutralCount(neutralCount);
                    rec.setTotalVotes(forCount + againstCount + neutralCount);
                    // Majority FOR = Pass; majority AGAINST or NEUTRAL = Fail.
                    if (forCount > (againstCount + neutralCount)) {
                        rec.setResult("PASSED");
                    } else {
                        rec.setResult("FAILED");
                    }
                    rec.setClosedAt(LocalDateTime.now());
                    ballotRecordRepository.save(rec);
                });
    }

    public List<BallotRecord> listBySession(Session session) {
        return ballotRecordRepository.findBySessionOrderByIdDesc(session);
    }

    public List<BallotRecord> listOpenBySession(Session session) {
        return ballotRecordRepository.findBySessionAndStatusOrderByIdDesc(session, "OPEN");
    }

    public Optional<BallotRecord> getById(Long id) {
        return ballotRecordRepository.findById(id);
    }
}
