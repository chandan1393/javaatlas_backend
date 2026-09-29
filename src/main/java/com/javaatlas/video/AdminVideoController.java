package com.javaatlas.video;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Video uploads for the course editor (admin only, behind two-factor sign-in like the rest of /api/admin). */
@RestController
@RequestMapping("/api/admin/videos")
public class AdminVideoController {

    private static final Logger log = LoggerFactory.getLogger(AdminVideoController.class);

    public record NewVideo(@NotBlank @Size(max = 200) String title) {
    }

    private final BunnyStreamService bunny;

    public AdminVideoController(BunnyStreamService bunny) {
        this.bunny = bunny;
    }

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("uploads", bunny.uploadsEnabled(), "signedPlayback", bunny.signedPlayback());
    }

    /** Creates the video in Bunny and returns a signed ticket the browser uses to upload the file directly. */
    @PostMapping
    public BunnyStreamService.UploadTicket create(@Valid @RequestBody NewVideo req, @AuthenticationPrincipal Jwt jwt) {
        BunnyStreamService.UploadTicket ticket = bunny.createUpload(req.title().trim());
        log.info("Admin {} started a video upload: {} ({})", jwt.getClaimAsString("email"), req.title(), ticket.videoId());
        return ticket;
    }

    @GetMapping
    public List<BunnyStreamService.VideoInfo> recent() {
        return bunny.recent();
    }

    @GetMapping("/{id}")
    public BunnyStreamService.VideoInfo info(@PathVariable String id) {
        return bunny.info(id);
    }

    /** A signed playback link for previewing in the editor. */
    @GetMapping("/{id}/play")
    public Map<String, String> play(@PathVariable String id) {
        BunnyStreamService.requireGuid(id);
        return Map.of("url", bunny.playbackUrl(BunnyStreamService.PREFIX + id));
    }
}
