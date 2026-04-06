package com.gm2dev.calendar_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class CalendarServiceApplicationTest {

    @MockitoBean
    private GoogleCalendarService googleCalendarService;

    @Test
    void contextLoads() {
    }
}
