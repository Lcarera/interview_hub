package com.gm2dev.calendar_service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class CalendarServiceApplicationTest {

    @Test
    void contextLoads() {
        // Verifies the Spring context starts with mocked Google Calendar config.
    }
}
