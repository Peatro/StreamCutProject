package com.peatroxd.streamcutproject.workertask;

public enum WorkerTaskStatus {
    QUEUED,
    CLAIMED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED
}
