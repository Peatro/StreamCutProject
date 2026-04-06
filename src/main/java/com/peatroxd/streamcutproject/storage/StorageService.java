package com.peatroxd.streamcutproject.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

public interface StorageService {

    Path resolveJobRoot(long jobId);

    Path resolveSourceVideoPath(long jobId, String originalFilename);

    Path storeSourceVideo(long jobId, String originalFilename, InputStream content) throws IOException;

    Path resolveAudioPath(long jobId);

    Path resolveExportedClipPath(long jobId, long candidateId, String extension);
}
