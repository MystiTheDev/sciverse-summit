package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationControllerTest {

    private MockMvc mockMvc;
    private NotificationService notificationService;

    private static Principal as(String name) {
        return () -> name;
    }

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        NotificationController controller = new NotificationController();
        ReflectionTestUtils.setField(controller, "notificationService", notificationService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getNotifications_unauthenticated_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    // ---- mutations now report honestly instead of always answering "ok" ----

    @Test
    void markRead_owned_succeeds() throws Exception {
        when(notificationService.markRead(anyLong(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/notifications/read").param("id", "1").principal(as("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void markRead_notOwner_returns404() throws Exception {
        when(notificationService.markRead(anyLong(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/notifications/read").param("id", "1").principal(as("mallory")))
                .andExpect(status().isNotFound());
    }

    @Test
    void markRead_unauthenticated_returns401_andMutatesNothing() throws Exception {
        mockMvc.perform(post("/api/notifications/read").param("id", "1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationService);
    }

    @Test
    void clearAll_succeeds() throws Exception {
        when(notificationService.clearAll(anyString())).thenReturn(5L);

        mockMvc.perform(post("/api/notifications/clear-all").principal(as("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void clearAll_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/notifications/clear-all"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationService);
    }

    @Test
    void deleteOne_succeeds() throws Exception {
        when(notificationService.deleteOne(anyLong(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/notifications/delete").param("id", "1").principal(as("alice")))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void deleteOne_notOwner_returns404() throws Exception {
        when(notificationService.deleteOne(anyLong(), anyString())).thenReturn(false);

        mockMvc.perform(post("/api/notifications/delete").param("id", "1").principal(as("mallory")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteOne_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/notifications/delete").param("id", "1"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(notificationService);
    }

    /**
     * The endpoint had no client callers at all, so it was removed rather than
     * left as an unused surface. This pins that decision.
     */
    @Test
    void readAllEndpoint_isGone() throws Exception {
        mockMvc.perform(post("/api/notifications/read-all").principal(as("alice")))
                .andExpect(status().isNotFound());
    }
}
