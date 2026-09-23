package com.ishan.sciverse.summit.repository;

import com.ishan.sciverse.summit.entity.Notification;
import com.ishan.sciverse.summit.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Most recent first. */
    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    long countByUserAndReadFalse(User user);

    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.user = ?1")
    long deleteByUser(User user);

    /** Outbox: find failed notifications ready for retry. */
    List<Notification> findByStatusAndNextRetryAtBefore(String status, LocalDateTime threshold);

    /** Outbox: count notifications in a given status for a user. */
    long countByUserAndStatus(User user, String status);

    /** Cleanup: delete notifications older than the given date. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    long deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);

    /** Cleanup: find oldest notifications for a user beyond the max count. */
    @Query("SELECT n FROM Notification n WHERE n.user = :user ORDER BY n.createdAt DESC")
    List<Notification> findOldestForUser(@Param("user") User user, org.springframework.data.domain.Pageable pageable);
}
