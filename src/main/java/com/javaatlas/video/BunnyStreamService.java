package com.javaatlas.video;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.javaatlas.AppProperties;
import com.javaatlas.common.ApiException;

/**
 * Bunny Stream: creates videos for direct browser uploads (TUS), reads their processing status, and signs
 * playback links so only the learners we send them to can watch, and only for a limited time.
 */
@Service
public class BunnyStreamService {

    public static final String PREFIX = "bunny:";
    public static final String TUS_ENDPOINT = "https://video.bunnycdn.com/tusupload";
    private static final Pattern GUID = Pattern.compile("[0-9a-fA-F-]{36}");
    private static final Duration UPLOAD_WINDOW = Duration.ofHours(24);

    /** What the browser needs to upload one file straight to Bunny. The API key itself is never included. */
    public record UploadTicket(String videoId, String libraryId, long expires, String signature, String endpoint) {
    }

    /** Status codes from Bunny: 0 created, 1 uploaded, 2 processing, 3 transcoding, 4 finished, 5 error, 6 upload failed. */
    public record VideoInfo(String videoId, String title, int status, String statusText, int encodeProgress, int lengthSeconds, boolean ready) {
    }

    private final AppProperties.Video cfg;
    private final RestClient http;

    public BunnyStreamService(AppProperties props) {
        this.cfg = props.video();
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(10));
        rf.setReadTimeout(Duration.ofSeconds(30));
        this.http = RestClient.builder().baseUrl("https://video.bunnycdn.com").requestFactory(rf).build();
    }

    public boolean uploadsEnabled() {
        return cfg.uploadsEnabled();
    }

    public boolean signedPlayback() {
        return cfg.signedPlayback();
    }

    /**
     * Turns a stored video reference into something the player can open. "bunny:<id>" becomes an embed link,
     * signed and time-limited when a token key is configured; other links are returned unchanged.
     * Call this only after checking that the viewer may watch the lecture.
     */
    public String playbackUrl(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return stored;
        String id = stored.substring(PREFIX.length());
        if (!GUID.matcher(id).matches() || !AppProperties.has(cfg.bunnyLibraryId())) return null;
        String base = "https://iframe.mediadelivery.net/embed/" + cfg.bunnyLibraryId() + "/" + id;
        if (!cfg.signedPlayback()) return base;
        long expires = Instant.now().plus(Duration.ofMinutes(cfg.tokenTtlMinutes())).getEpochSecond();
        return base + "?token=" + sha256(cfg.bunnyTokenKey() + id + expires) + "&expires=" + expires;
    }

    /** Creates an empty video in the library and signs a 24-hour upload for it. */
    public UploadTicket createUpload(String title) {
        requireUploads();
        Map<?, ?> created;
        try {
            created = http.post()
                    .uri("/library/{lib}/videos", cfg.bunnyLibraryId())
                    .header("AccessKey", cfg.bunnyApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("title", title))
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientException e) {
            throw upstream(e);
        }
        String videoId = created == null ? null : String.valueOf(created.get("guid"));
        if (videoId == null || !GUID.matcher(videoId).matches()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "video_error", "Bunny Stream didn’t create the video. Check the library ID and API key.");
        }
        long expires = Instant.now().plus(UPLOAD_WINDOW).getEpochSecond();
        String signature = sha256(cfg.bunnyLibraryId() + cfg.bunnyApiKey() + expires + videoId);
        return new UploadTicket(videoId, cfg.bunnyLibraryId(), expires, signature, TUS_ENDPOINT);
    }

    public VideoInfo info(String videoId) {
        requireUploads();
        requireGuid(videoId);
        try {
            Map<?, ?> v = http.get()
                    .uri("/library/{lib}/videos/{id}", cfg.bunnyLibraryId(), videoId)
                    .header("AccessKey", cfg.bunnyApiKey())
                    .retrieve()
                    .body(Map.class);
            return toInfo(v);
        } catch (RestClientException e) {
            throw upstream(e);
        }
    }

    /** The most recent videos in the library, so a video uploaded in the Bunny dashboard can be picked. */
    public List<VideoInfo> recent() {
        requireUploads();
        try {
            Map<?, ?> page = http.get()
                    .uri("/library/{lib}/videos?page=1&itemsPerPage=100&orderBy=date", cfg.bunnyLibraryId())
                    .header("AccessKey", cfg.bunnyApiKey())
                    .retrieve()
                    .body(Map.class);
            Object items = page == null ? null : page.get("items");
            if (!(items instanceof List<?> list)) return List.of();
            return list.stream().filter(Map.class::isInstance).map(m -> toInfo((Map<?, ?>) m)).toList();
        } catch (RestClientException e) {
            throw upstream(e);
        }
    }

    private static VideoInfo toInfo(Map<?, ?> v) {
        if (v == null) throw new ApiException(HttpStatus.NOT_FOUND, "video_not_found", "That video doesn’t exist in the library.");
        int status = number(v.get("status"));
        String text = switch (status) {
            case 0 -> "Waiting for upload";
            case 1 -> "Uploaded";
            case 2 -> "Processing";
            case 3 -> "Encoding";
            case 4 -> "Ready";
            case 5 -> "Encoding failed";
            case 6 -> "Upload failed";
            default -> "Processing";
        };
        return new VideoInfo(String.valueOf(v.get("guid")), String.valueOf(v.get("title")), status, text,
                number(v.get("encodeProgress")), number(v.get("length")), status == 4);
    }

    private static int number(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private void requireUploads() {
        if (!cfg.uploadsEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "video_disabled", "Video uploads aren’t set up. Add your Bunny Stream library ID and API key.");
        }
    }

    public static void requireGuid(String id) {
        if (id == null || !GUID.matcher(id).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid_request", "That isn’t a valid video ID.");
        }
    }

    private static ApiException upstream(RestClientException e) {
        return new ApiException(HttpStatus.BAD_GATEWAY, "video_error", "Bunny Stream didn’t respond as expected. Check the library ID and API key.");
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
