package com.peatroxd.streamcutproject.storage;

import java.nio.file.Path;

public interface StorageService {

    Path resolveJobRoot(long jobId);

    Path resolveSourceVideoPath(long jobId, String originalFilename);

    Path resolveAudioPath(long jobId);

    Path resolveExportedClipPath(long jobId, long candidateId, String extension);
}
