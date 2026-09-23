package com.ishan.sciverse.summit.controller;

import com.ishan.sciverse.summit.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class NotificationControllerTest {

    private MockMvc mockMvc;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController()).build();
        // Inject mock via reflection since NotificationController uses @Autowired
        try {
            var field = NotificationController.class.getDeclaredField("notificationService");
            field.setAccessible(true);
            field.set(new NotificationController(), notificationService);
        } catch (Exception e) {
            // If injection fails, create controller with mock
            NotificationController controller = new NotificationController();
            try {
                var field = NotificationController.class.getDeclaredField("notificationService");
                field.setAccessible(true);
                // Use the controller instance that MockMvc is using
            } catch (Exception ignored) {}
        }
        // Rebuild with the controller that has the mock
        NotificationController controller = new NotificationController();
        try {
            var field = NotificationController.class.getDeclaredField("notificationService");
            field.setAccessible(true);
            field.set(controller, notificationService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void getNotifications_unauthenticated_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void markRead_succeeds() throws Exception {
        doNothing().when(notificationService).markRead(anyLong(), anyString());

        mockMvc.perform(post("/api/notifications/read").param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void markAllRead_succeeds() throws Exception {
        doNothing().when(notificationService).markAllRead(anyString());

        mockMvc.perform(post("/api/notifications/read-all"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void clearAll_succeeds() throws Exception {
        when(notificationService.clearAll(anyString())).thenReturn(5L);

        mockMvc.perform(post("/api/notifications/clear-all"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }

    @Test
    void deleteOne_succeeds() throws Exception {
        when(notificationService.deleteOne(anyLong(), anyString())).thenReturn(true);

        mockMvc.perform(post("/api/notifications/delete").param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(content().string("ok"));
    }
}
