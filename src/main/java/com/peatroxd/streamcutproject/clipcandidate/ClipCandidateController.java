package com.peatroxd.streamcutproject.clipcandidate;

import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidateResponse;
import com.peatroxd.streamcutproject.clipcandidate.api.ExportStatusResponse;
import com.peatroxd.streamcutproject.vodjob.VodJobService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClipCandidateController {

    private final VodJobService vodJobService;

    public ClipCandidateController(VodJobService vodJobService) {
        this.vodJobService = vodJobService;
    }

    @PostMapping("/api/candidates/{id}/approve")
    public ClipCandidateResponse approveCandidate(@PathVariable Long id) {
        return vodJobService.approveCandidate(id);
    }

    @PostMapping("/api/candidates/{id}/reject")
    public ClipCandidateResponse rejectCandidate(@PathVariable Long id) {
        return vodJobService.rejectCandidate(id);
    }

    @PostMapping("/api/candidates/{id}/export")
    public ExportStatusResponse exportCandidate(@PathVariable Long id) {
        return vodJobService.startExport(id);
    }

    @GetMapping("/api/exports/{id}")
    public ExportStatusResponse getExportStatus(@PathVariable Long id) {
        return vodJobService.getExportStatus(id);
    }
}
