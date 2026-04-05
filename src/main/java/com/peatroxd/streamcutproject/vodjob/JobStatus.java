package com.peatroxd.streamcutproject.vodjob;

public enum JobStatus {
    NEW,
    QUEUED,
    DOWNLOADING,
    EXTRACTING_AUDIO,
    TRANSCRIBING,
    DETECTING_SILENCE,
    ANALYZING_WINDOWS,
    GENERATING_CANDIDATES,
    READY_FOR_REVIEW,
    EXPORTING_CLIP,
    COMPLETED,
    FAILED
}
