package com.ishan.sciverse.summit.repository;

import com.ishan.sciverse.summit.entity.DelegateMembership;
import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DelegateMembershipRepository extends JpaRepository<DelegateMembership, Long> {
    List<DelegateMembership> findByUserOrderByIdDesc(User user);
    Optional<DelegateMembership> findTopByUserOrderByIdDesc(User user);
    Optional<DelegateMembership> findByUserAndSession(User user, Session session);
    List<DelegateMembership> findBySession(Session session);
}
