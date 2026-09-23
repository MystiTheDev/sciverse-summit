package com.ishan.sciverse.summit.repository;

import com.ishan.sciverse.summit.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findTop100ByOrderByCreatedAtDesc();

    @Query("SELECT a FROM AuditLog a "
            + "WHERE (:sessionId IS NULL OR a.sessionId = :sessionId) "
            + "AND (:action IS NULL OR a.action = :action) "
            + "AND (:actor IS NULL OR LOWER(a.actorUsername) LIKE LOWER(CONCAT('%', CAST(:actor AS string), '%'))) "
            + "AND (:q IS NULL OR LOWER(a.details) LIKE LOWER(CONCAT('%', CAST(:q AS string), '%'))) "
            + "ORDER BY a.createdAt DESC")
    List<AuditLog> search(@Param("sessionId") Long sessionId,
                          @Param("action") String action,
                          @Param("actor") String actor,
                          @Param("q") String q);
}
