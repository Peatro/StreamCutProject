package com.peatroxd.streamcutproject.storage;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;

public interface ArtifactStorageService {

    String storeSourceVideo(long jobId, String originalFilename, Path localArtifactPath) throws IOException;

    String storeCompletedExport(long jobId, long candidateId, Path localArtifactPath) throws IOException;

    boolean exists(String reference);

    void delete(String reference) throws IOException;

    Optional<Path> resolveLocalPath(String reference);

    Optional<URI> createSignedGetUri(String reference);

    ArtifactResource open(String reference) throws IOException;
}
