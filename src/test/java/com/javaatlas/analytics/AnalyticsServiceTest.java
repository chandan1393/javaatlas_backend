package com.javaatlas.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AnalyticsServiceTest {

    @Test
    void detectsDeviceFromUserAgent() {
        assertEquals("mobile", AnalyticsService.device("Mozilla/5.0 (Linux; Android 14) Mobile Safari"));
        assertEquals("tablet", AnalyticsService.device("Mozilla/5.0 (iPad; CPU OS 17_0 like Mac OS X)"));
        assertEquals("desktop", AnalyticsService.device("Mozilla/5.0 (Windows NT 10.0; Win64; x64)"));
    }
}
