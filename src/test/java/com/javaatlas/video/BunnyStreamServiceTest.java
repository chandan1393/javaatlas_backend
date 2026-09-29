package com.javaatlas.video;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.javaatlas.AppProperties;

class BunnyStreamServiceTest {

    private static final String ID = "32d140e2-e4f4-4eec-9d53-20371e9be607";

    private static BunnyStreamService service(String tokenKey) {
        AppProperties props = new AppProperties(null, false, List.of(), null, false, null, null, null, null,
                new AppProperties.Video("12345", "api-key", tokenKey, 30));
        return new BunnyStreamService(props);
    }

    @Test
    void signsPlaybackLinksWithKeyVideoIdAndExpiry() {
        String url = service("4742a81b-bf15-42fe-8b1c-8fcb9024c550").playbackUrl("bunny:" + ID);
        Matcher m = Pattern.compile("https://iframe\\.mediadelivery\\.net/embed/12345/" + ID + "\\?token=([0-9a-f]{64})&expires=(\\d+)").matcher(url);
        assertTrue(m.matches(), url);
        long expires = Long.parseLong(m.group(2));
        assertEquals(BunnyStreamService.sha256("4742a81b-bf15-42fe-8b1c-8fcb9024c550" + ID + expires), m.group(1));
        long inMinutes = (expires - System.currentTimeMillis() / 1000) / 60;
        assertTrue(inMinutes >= 29 && inMinutes <= 30, "expires in ~30 minutes");
    }

    @Test
    void leavesOtherLinksAloneAndRejectsBadIds() {
        BunnyStreamService s = service("");
        assertEquals("https://www.youtube.com/watch?v=abc", s.playbackUrl("https://www.youtube.com/watch?v=abc"));
        assertEquals("https://iframe.mediadelivery.net/embed/12345/" + ID, s.playbackUrl("bunny:" + ID));
        assertNull(s.playbackUrl("bunny:not-a-guid"));
        assertNull(s.playbackUrl(null));
        assertTrue(Duration.ofMinutes(30).toMinutes() == 30);
    }
}
