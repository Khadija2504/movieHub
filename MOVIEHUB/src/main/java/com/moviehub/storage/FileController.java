package com.moviehub.storage;

import java.nio.file.Path;
import java.time.Duration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.moviehub.common.exception.ResourceNotFoundException;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

/** Public file serving. File names are random and immutable, so responses are cached for a long time. */
@RestController
@Tag(name = "Files")
@SecurityRequirements
public class FileController {

    private final AvatarStorage avatars;

    public FileController(AvatarStorage avatars) {
        this.avatars = avatars;
    }

    @GetMapping("/api/files/avatars/{fileName}")
    @Operation(summary = "Serve an avatar image")
    public ResponseEntity<Resource> avatar(@PathVariable String fileName) {
        Path path = avatars.resolve(fileName).orElseThrow(() -> new ResourceNotFoundException("Image not found"));
        MediaType type = fileName.endsWith(".png") ? MediaType.IMAGE_PNG
                : fileName.endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(path));
    }
}
