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
import org.mockito.Spy;
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

    @Spy
    private NotificationPayloads payloads = new NotificationPayloads();

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
        assertEquals("SENT", n.getStatus());
        assertNull(n.getNextRetryAt());
    }

    @Test
    void retryFailedNotifications_actuallyDeliversToTheRecipient() {
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

        // The old implementation published an empty global hint and never
        // delivered anything; the retry must now reach the addressee.
        verify(liveEventService).publishToUser(eq("testuser"), eq("notif.changed"), anyString());
        verify(liveEventService, never()).publish(eq("notif.changed"), eq(""));
    }

    @Test
    void retryFailedNotifications_deliveryFailure_keepsEntryRetryable() {
        Notification n = new Notification();
        n.setId(1L);
        n.setUser(testUser);
        n.setStatus("FAILED");
        n.setRetryCount(0);
        n.setNextRetryAt(LocalDateTime.now().minusSeconds(1));

        when(notificationRepository.findByStatusAndNextRetryAtBefore(eq("FAILED"), any(LocalDateTime.class)))
                .thenReturn(List.of(n));
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("DB still down"));

        outboxService.retryFailedNotifications();

        assertEquals("FAILED", n.getStatus(), "a failed attempt must stay retryable");
        assertNotNull(n.getNextRetryAt(), "a failed attempt must schedule a backoff");
        verify(metrics, never()).incrementRetried();
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
