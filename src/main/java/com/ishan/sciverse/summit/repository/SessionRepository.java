package com.ishan.sciverse.summit.repository;

import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionRepository extends JpaRepository<Session, Long> {
    List<Session> findByUser(User user);
    Optional<Session> findTopByUserOrderByIdDesc(User user);
    Optional<Session> findTopByUserAndActiveTrueOrderByIdDesc(User user);
}
