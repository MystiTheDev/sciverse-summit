package com.ishan.sciverse.summit.repository;

import com.ishan.sciverse.summit.entity.Notification;
import com.ishan.sciverse.summit.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guards the per-user notification trim against silent data loss.
 *
 * <p>{@code findOldestForUser} used to be ordered {@code createdAt DESC}, so
 * {@code enforceMaxPerUser} deleted each user's <em>newest</em> notifications
 * and kept the stalest — the opposite of what a retention cap should do. This
 * runs against a real in-memory database because the bug lived in the JPQL
 * ordering, which a mocked repository cannot exercise.
 */
@DataJpaTest
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private TestEntityManager entityManager;

    /** User has @NotBlank/@Email constraints, so fill every required field. */
    private static User newUser(String prefix) {
        User u = new User();
        String tag = prefix + System.nanoTime();
        u.setFullName(prefix);
        u.setUsername(tag);
        u.setEmail(tag + "@example.com");
        u.setPassword("password");
        return u;
    }

    private Notification persist(User user, String title, LocalDateTime createdAt) {
        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setMessage("body");
        n.setType("GENERAL");
        // @PrePersist stamps createdAt, so save first then backdate and
        // re-save to control the ordering precisely.
        Notification saved = entityManager.persistAndFlush(n);
        saved.setCreatedAt(createdAt);
        notificationRepository.save(saved);
        entityManager.flush();
        entityManager.clear();
        return saved;
    }

    @Test
    void findOldestForUser_returnsStalestFirst() {
        User user = newUser("alice");
        entityManager.persistAndFlush(user);

        LocalDateTime base = LocalDateTime.now().minusDays(10);
        persist(user, "oldest", base);
        persist(user, "middle", base.plusDays(1));
        persist(user, "newest", base.plusDays(2));

        List<Notification> result = notificationRepository.findOldestForUser(user, PageRequest.of(0, 2));

        assertEquals(2, result.size());
        assertEquals("oldest", result.get(0).getTitle(),
                "the trim must start from the stalest notification");
        assertEquals("middle", result.get(1).getTitle());
    }

    @Test
    void findOldestForUser_keepsNewestOutOfTheTrim() {
        User user = newUser("bob");
        entityManager.persistAndFlush(user);

        LocalDateTime base = LocalDateTime.now().minusDays(10);
        persist(user, "oldest", base);
        persist(user, "newest", base.plusDays(2));

        List<Notification> toDelete = notificationRepository.findOldestForUser(user, PageRequest.of(0, 1));
        notificationRepository.deleteAll(toDelete);
        entityManager.flush();
        entityManager.clear();

        List<Notification> remaining = notificationRepository.findByUserOrderByCreatedAtDesc(user);
        assertEquals(1, remaining.size());
        assertEquals("newest", remaining.get(0).getTitle(),
                "trimming must never discard a user's most recent notification");
    }
}
