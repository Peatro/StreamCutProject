package com.peatroxd.streamcutproject.storage;

import java.io.InputStream;

public record ArtifactResource(
        InputStream inputStream,
        long contentLength,
        String contentType,
        String filename
) {
}
