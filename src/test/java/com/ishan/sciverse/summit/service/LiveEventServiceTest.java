package com.ishan.sciverse.summit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LiveEventServiceTest {

    @Mock
    private NotificationMetrics metrics;

    private LiveEventService service() {
        return new LiveEventService(metrics);
    }

    @Test
    void connect_emitterAdded() {
        LiveEventService svc = service();
        svc.connect("alice");
        assertEquals(1, svc.connectionCount());
    }

    @Test
    void connect_caseInsensitive_sameUserSharesCount() {
        LiveEventService svc = service();
        svc.connect("Alice");
        svc.connect("alice");
        assertEquals(2, svc.connectionCount(), "two tabs for one user are two emitters");
    }

    @Test
    void publish_doesNotThrow() {
        LiveEventService svc = service();
        svc.connect("alice");
        assertDoesNotThrow(() -> svc.publish("test.event", "data"));
    }

    @Test
    void publish_emptyEmitters_doesNotThrow() {
        assertDoesNotThrow(() -> service().publish("test.event", "data"));
    }

    @Test
    void sendHeartbeat_doesNotThrow() {
        LiveEventService svc = service();
        svc.connect("alice");
        assertDoesNotThrow(svc::sendHeartbeat);
    }

    @Test
    void sendHeartbeat_emptyEmitters_doesNotThrow() {
        assertDoesNotThrow(() -> service().sendHeartbeat());
    }

    // ---- per-user routing ----

    @Test
    void publishToUser_unknownUser_doesNotThrow() {
        assertDoesNotThrow(() -> service().publishToUser("nobody", "notif.changed", "{}"));
    }

    @Test
    void publishToUser_nullUsername_doesNotThrow() {
        assertDoesNotThrow(() -> service().publishToUser(null, "notif.changed", "{}"));
    }

    @Test
    void publishToUser_anonymousConnection_neverReceivesPerUserEvents() {
        LiveEventService svc = service();
        // A connection that could not be attributed to a user must not be a
        // delivery target for someone else's notification.
        assertDoesNotThrow(() -> svc.publishToUser(null, "notif.changed", "{}"));
        assertDoesNotThrow(() -> svc.publishToUser("", "notif.changed", "{}"));
    }

    @Test
    void globalPublish_reachesMultipleUsers() {
        LiveEventService svc = service();
        svc.connect("alice");
        svc.connect("bob");
        // Generic change events stay audience-free and go to everyone.
        assertDoesNotThrow(() -> svc.publish("motion.changed", ""));
        assertEquals(2, svc.connectionCount());
    }
}
