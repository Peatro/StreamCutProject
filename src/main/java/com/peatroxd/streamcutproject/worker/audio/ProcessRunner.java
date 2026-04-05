package com.peatroxd.streamcutproject.worker.audio;

import java.io.IOException;
import java.util.List;

public interface ProcessRunner {

    ProcessExecutionResult run(List<String> command) throws IOException, InterruptedException;
}
