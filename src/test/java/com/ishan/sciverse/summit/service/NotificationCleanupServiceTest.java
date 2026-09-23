package com.ishan.sciverse.summit.service;

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

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationCleanupServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationMetrics metrics;

    @InjectMocks
    private NotificationCleanupService cleanupService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(cleanupService, "ttlDays", 30);
        ReflectionTestUtils.setField(cleanupService, "maxPerUser", 200);
    }

    @Test
    void cleanupOldNotifications_deletesOldEntries() {
        when(notificationRepository.deleteByCreatedAtBefore(any())).thenReturn(5L);

        cleanupService.cleanupOldNotifications();

        verify(notificationRepository).deleteByCreatedAtBefore(any());
        verify(metrics, times(5)).incrementCleaned();
    }

    @Test
    void cleanupOldNotifications_noOldEntries_noMetrics() {
        when(notificationRepository.deleteByCreatedAtBefore(any())).thenReturn(0L);

        cleanupService.cleanupOldNotifications();

        verify(metrics, never()).incrementCleaned();
    }

    @Test
    void enforceMaxPerUser_noUsers_doesNothing() {
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        cleanupService.enforceMaxPerUser();

        verify(notificationRepository, never()).countByUserAndReadFalse(any());
    }
}
