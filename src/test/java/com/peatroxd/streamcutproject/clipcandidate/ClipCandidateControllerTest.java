package com.peatroxd.streamcutproject.clipcandidate;

import com.peatroxd.streamcutproject.clipcandidate.api.ClipCandidateResponse;
import com.peatroxd.streamcutproject.clipcandidate.api.ExportStatusResponse;
import com.peatroxd.streamcutproject.vodjob.VodJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ClipCandidateControllerTest {

    private VodJobService vodJobService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        vodJobService = Mockito.mock(VodJobService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new ClipCandidateController(vodJobService)).build();
    }

    @Test
    void approvesCandidate() throws Exception {
        when(vodJobService.approveCandidate(anyLong())).thenReturn(new ClipCandidateResponse(
                7L,
                5.0,
                12.0,
                0.91,
                "A candidate excerpt",
                "APPROVED",
                null,
                null
        ));

        mockMvc.perform(post("/api/candidates/7/approve"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.moderationStatus").value("APPROVED"));
    }

    @Test
    void rejectsCandidate() throws Exception {
        when(vodJobService.rejectCandidate(anyLong())).thenReturn(new ClipCandidateResponse(
                7L,
                5.0,
                12.0,
                0.91,
                "A candidate excerpt",
                "REJECTED",
                null,
                null
        ));

        mockMvc.perform(post("/api/candidates/7/reject"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.moderationStatus").value("REJECTED"));
    }

    @Test
    void startsExportForApprovedCandidate() throws Exception {
        when(vodJobService.startExport(anyLong())).thenReturn(new ExportStatusResponse(
                7L,
                1L,
                "EXPORTING_CLIP",
                "/var/lib/streamcut/jobs/1/exports/candidate-7.mp4",
                "APPROVED"
        ));

        mockMvc.perform(post("/api/candidates/7/export"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("EXPORTING_CLIP"))
                .andExpect(jsonPath("$.artifactPath").value("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4"));
    }

    @Test
    void getsExportStatus() throws Exception {
        when(vodJobService.getExportStatus(anyLong())).thenReturn(new ExportStatusResponse(
                7L,
                1L,
                "EXPORTING_CLIP",
                "/var/lib/streamcut/jobs/1/exports/candidate-7.mp4",
                "APPROVED"
        ));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/exports/7"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.status").value("EXPORTING_CLIP"))
                .andExpect(jsonPath("$.artifactPath").value("/var/lib/streamcut/jobs/1/exports/candidate-7.mp4"));
    }
}
