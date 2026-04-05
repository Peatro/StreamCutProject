package com.peatroxd.streamcutproject.clipcandidate;

import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidateResponse;
import com.peatroxd.streamcutproject.vodjob.VodJobService;
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
}
