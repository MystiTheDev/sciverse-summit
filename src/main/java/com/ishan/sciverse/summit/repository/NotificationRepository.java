package com.ishan.sciverse.summit.repository;

import com.ishan.sciverse.summit.entity.Notification;
import com.ishan.sciverse.summit.entity.User;
import org.springframework.data.domain.Pageable;
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

    /** Cleanup: delete notifications older than the given date. */
    @Modifying
    @Transactional
    @Query("DELETE FROM Notification n WHERE n.createdAt < :cutoff")
    long deleteByCreatedAtBefore(@Param("cutoff") LocalDateTime cutoff);

    /**
     * Cleanup: the oldest notifications for a user, so trimming to
     * {@code notification.max-per-user} discards the stalest rows.
     *
     * <p>Must stay {@code ASC}. This previously read {@code DESC}, which made
     * the per-user trim delete each user's <em>newest</em> notifications and
     * keep the oldest — the exact opposite of the intent, and a silent loss of
     * recent alerts.
     */
    @Query("SELECT n FROM Notification n WHERE n.user = :user ORDER BY n.createdAt ASC")
    List<Notification> findOldestForUser(@Param("user") User user, Pageable pageable);
}
