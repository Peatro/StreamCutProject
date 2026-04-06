package com.peatroxd.streamcutproject.storage;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Path;

public final class PathSafety {

    private PathSafety() {
    }

    public static Path requireWithinRoot(Path root, Path candidate, String description) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    description + " must stay within the configured storage root"
            );
        }
        return candidate.normalize();
    }
}
