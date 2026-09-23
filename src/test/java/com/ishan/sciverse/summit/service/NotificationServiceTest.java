package com.ishan.sciverse.summit.service;

import com.ishan.sciverse.summit.entity.DelegateMembership;
import com.ishan.sciverse.summit.entity.Notification;
import com.ishan.sciverse.summit.entity.Session;
import com.ishan.sciverse.summit.entity.User;
import com.ishan.sciverse.summit.repository.DelegateMembershipRepository;
import com.ishan.sciverse.summit.repository.NotificationRepository;
import com.ishan.sciverse.summit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DelegateMembershipRepository membershipRepository;

    @Mock
    private LiveEventService liveEventService;

    @Mock
    private NotificationOutboxService outboxService;

    @Mock
    private NotificationMetrics metrics;

    @InjectMocks
    private NotificationService notificationService;

    private User testUser;
    private Session testSession;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setUsername("testuser");

        testSession = new Session();
        testSession.setId(1L);
        testSession.setUser(testUser);
    }

    @Test
    void notifyUser_succeeds_notificationSaved() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(1L);
            return n;
        });

        Notification result = notificationService.notifyUser(testUser, "Test Title", "Test Message", "GENERAL", "/test");

        assertNotNull(result);
        assertEquals("Test Title", result.getTitle());
        assertEquals("Test Message", result.getMessage());
        assertEquals("GENERAL", result.getType());
        assertEquals("/test", result.getLink());
        assertFalse(result.isRead());
        verify(notificationRepository).save(any(Notification.class));
        verify(metrics).incrementCreated();
    }

    @Test
    void notifyUser_nullType_defaultsToGeneral() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(1L);
            return n;
        });

        Notification result = notificationService.notifyUser(testUser, "Title", "Msg", null, null);

        assertEquals("GENERAL", result.getType());
    }

    @Test
    void notifyUser_unknownType_defaultsToGeneral() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(1L);
            return n;
        });

        Notification result = notificationService.notifyUser(testUser, "Title", "Msg", "UNKNOWN_TYPE", null);

        assertEquals("GENERAL", result.getType());
    }

    @Test
    void notifyUser_longTitle_truncatedTo200Chars() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(1L);
            return n;
        });

        String longTitle = "A".repeat(300);
        Notification result = notificationService.notifyUser(testUser, longTitle, "Msg", "GENERAL", null);

        assertEquals(200, result.getTitle().length());
    }

    @Test
    void notifyUser_longMessage_truncatedTo2000Chars() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(1L);
            return n;
        });

        String longMsg = "B".repeat(3000);
        Notification result = notificationService.notifyUser(testUser, "Title", longMsg, "GENERAL", null);

        assertEquals(2000, result.getMessage().length());
    }

    @Test
    void notifyUser_nonInternalLink_rejected() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(1L);
            return n;
        });

        Notification result = notificationService.notifyUser(testUser, "Title", "Msg", "GENERAL", "https://evil.com");

        assertNull(result.getLink());
    }

    @Test
    void notifyUser_blankTitle_defaultsToNotification() {
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(1L);
            return n;
        });

        Notification result = notificationService.notifyUser(testUser, "   ", "Msg", "GENERAL", null);

        assertEquals("Notification", result.getTitle());
    }

    @Test
    void notifyUser_dbFailure_enqueuesToOutbox() {
        when(notificationRepository.save(any(Notification.class))).thenThrow(new RuntimeException("DB error"));

        assertThrows(RuntimeException.class, () ->
                notificationService.notifyUser(testUser, "Title", "Msg", "GENERAL", null));

        verify(outboxService).enqueueFailedNotification(testUser, "Title", "Msg", "GENERAL", null);
        verify(metrics).incrementFailed();
    }

    @Test
    void markRead_notOwner_doesNotModify() {
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setUsername("other");

        Notification n = new Notification();
        n.setId(1L);
        n.setUser(otherUser);
        n.setRead(false);

        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

        notificationService.markRead(1L, "testuser");

        assertFalse(n.isRead());
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void deleteOne_notOwner_returnsFalse() {
        User otherUser = new User();
        otherUser.setId(2L);
        otherUser.setUsername("other");

        Notification n = new Notification();
        n.setId(1L);
        n.setUser(otherUser);

        when(notificationRepository.findById(1L)).thenReturn(Optional.of(n));

        boolean result = notificationService.deleteOne(1L, "testuser");

        assertFalse(result);
        verify(notificationRepository, never()).delete(any());
    }

    @Test
    void notifySessionDelegates_batchOnlyOneSseEvent() {
        User user1 = new User();
        user1.setId(1L);
        user1.setUsername("user1");
        User user2 = new User();
        user2.setId(2L);
        user2.setUsername("user2");

        DelegateMembership m1 = new DelegateMembership();
        m1.setUser(user1);
        DelegateMembership m2 = new DelegateMembership();
        m2.setUser(user2);

        when(membershipRepository.findBySession(testSession)).thenReturn(List.of(m1, m2));
        when(notificationRepository.saveAll(any())).thenAnswer(invocation -> {
            List<Notification> list = invocation.getArgument(0);
            for (int i = 0; i < list.size(); i++) {
                list.get(i).setId((long) (i + 1));
            }
            return list;
        });

        int count = notificationService.notifySessionDelegates(testSession, "Title", "Msg", "VOTING", "/vote");

        assertEquals(2, count);
        verify(notificationRepository).saveAll(any());
        verify(metrics, times(2)).incrementCreated();
    }
}
