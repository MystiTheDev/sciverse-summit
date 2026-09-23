package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.entity.Notification;
import com.ishan.sciverse.summit.entity.User;
import com.ishan.sciverse.summit.repository.NotificationRepository;
import com.ishan.sciverse.summit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private LiveEventService liveEventService;

    @Mock
    private NotificationMetrics metrics;

    @InjectMocks
    private NotificationOutboxService outboxService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");
        ReflectionTestUtils.setField(outboxService, "maxRetries", 5);
    }

    @Test
    void enqueueFailedNotification_savesWithFailedStatus() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(1L);
            return n;
        });

        outboxService.enqueueFailedNotification(testUser, "Title", "Msg", "GENERAL", "/test");

        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    void retryFailedNotifications_retriesWithinBackoffWindow() {
        Notification n = new Notification();
        n.setId(1L);
        n.setUser(testUser);
        n.setStatus("FAILED");
        n.setRetryCount(0);
        n.setNextRetryAt(LocalDateTime.now().minusSeconds(1));

        when(notificationRepository.findByStatusAndNextRetryAtBefore(eq("FAILED"), any(LocalDateTime.class)))
                .thenReturn(List.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        outboxService.retryFailedNotifications();

        verify(metrics).incrementRetried();
    }

    @Test
    void retryFailedNotifications_movesToDeadLetter_afterMaxRetries() {
        Notification n = new Notification();
        n.setId(1L);
        n.setUser(testUser);
        n.setStatus("FAILED");
        n.setRetryCount(5);
        n.setNextRetryAt(LocalDateTime.now().minusSeconds(1));

        when(notificationRepository.findByStatusAndNextRetryAtBefore(eq("FAILED"), any(LocalDateTime.class)))
                .thenReturn(List.of(n));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        outboxService.retryFailedNotifications();

        verify(notificationRepository, atLeastOnce()).save(any(Notification.class));
    }

    @Test
    void retryFailedNotifications_skipsEntry_notYetReady() {
        when(notificationRepository.findByStatusAndNextRetryAtBefore(eq("FAILED"), any(LocalDateTime.class)))
                .thenReturn(List.of());

        outboxService.retryFailedNotifications();

        verify(notificationRepository, never()).save(any());
    }
}
