package com.ishan.sciverse.summit.repository;

import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoteRepository extends JpaRepository<Vote, Long> {
    List<Vote> findBySession(Session session);
    List<Vote> findBySessionAndBallotTypeAndBallotRefId(Session session, String ballotType, Long ballotRefId);
    Optional<Vote> findBySessionAndBallotTypeAndBallotRefIdAndDelegateName(
            Session session, String ballotType, Long ballotRefId, String delegateName);
}
