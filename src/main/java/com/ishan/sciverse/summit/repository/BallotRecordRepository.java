package com.ishan.sciverse.summit.repository;

import com.ishan.sciverse.summit.entity.BallotRecord;
import com.ishan.sciverse.summit.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BallotRecordRepository extends JpaRepository<BallotRecord, Long> {

    List<BallotRecord> findBySessionOrderByIdDesc(Session session);

    List<BallotRecord> findBySessionAndStatusOrderByIdDesc(Session session, String status);

    Optional<BallotRecord> findFirstBySessionAndBallotTypeAndBallotRefIdAndStatus(
            Session session, String ballotType, Long ballotRefId, String status);
}
