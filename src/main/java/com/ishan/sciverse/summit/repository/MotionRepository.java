package com.ishan.sciverse.summit.repository;

import com.ishan.sciverse.summit.entity.Motion;
import com.ishan.sciverse.summit.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MotionRepository extends JpaRepository<Motion, Long> {
    List<Motion> findBySessionOrderByIdDesc(Session session);
    List<Motion> findBySessionAndStatusOrderByIdDesc(Session session, String status);
}
