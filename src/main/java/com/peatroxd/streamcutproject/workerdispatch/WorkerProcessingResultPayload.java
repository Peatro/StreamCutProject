package com.peatroxd.streamcutproject.workerdispatch;

import com.peatroxd.streamcutproject.analysiswindow.AnalysisWindowWorkerPayload;
import com.peatroxd.streamcutproject.clipcandidate.ClipCandidateWorkerPayload;
import com.peatroxd.streamcutproject.silence.SilenceSegmentWorkerPayload;
import com.peatroxd.streamcutproject.transcript.TranscriptSegmentWorkerPayload;

import java.util.List;

public record WorkerProcessingResultPayload(
        Long jobId,
        Long durationSec,
        String language,
        String videoPath,
        String audioPath,
        List<TranscriptSegmentWorkerPayload> transcriptSegments,
        List<SilenceSegmentWorkerPayload> silenceSegments,
        List<AnalysisWindowWorkerPayload> analysisWindows,
        List<ClipCandidateWorkerPayload> clipCandidates
) {
}
