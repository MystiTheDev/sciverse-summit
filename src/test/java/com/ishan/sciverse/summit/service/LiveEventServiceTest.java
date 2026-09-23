package com.ishan.sciverse.summit.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LiveEventServiceTest {

    @InjectMocks
    private LiveEventService liveEventService;

    @Test
    void connect_emitterAdded() {
        liveEventService.connect();
        assertEquals(1, liveEventService.connectionCount());
    }

    @Test
    void publish_doesNotThrow() {
        liveEventService.connect();
        assertDoesNotThrow(() -> liveEventService.publish("test.event", "data"));
    }

    @Test
    void publish_emptyEmitters_doesNotThrow() {
        assertDoesNotThrow(() -> liveEventService.publish("test.event", "data"));
    }

    @Test
    void sendHeartbeat_doesNotThrow() {
        liveEventService.connect();
        assertDoesNotThrow(() -> liveEventService.sendHeartbeat());
    }

    @Test
    void sendHeartbeat_emptyEmitters_doesNotThrow() {
        assertDoesNotThrow(() -> liveEventService.sendHeartbeat());
    }
}
