(function () {
  const api = {
    listJobs: () => fetchJson("/api/jobs"),
    getJob: (id) => fetchJson(`/api/jobs/${id}`),
    getTranscript: (id) => fetchJson(`/api/jobs/${id}/transcript`),
    getEvents: (id) => fetchJson(`/api/jobs/${id}/events`),
    getCandidates: (id) => fetchJson(`/api/jobs/${id}/candidates`),
    cancelJob: (id) => postJson(`/api/jobs/${id}/cancel`),
    restartJob: (id) => postJson(`/api/jobs/${id}/restart`),
    createUrlJob: (url) => postJson("/api/jobs/url", {
      headers: {
        "Content-Type": "application/json"
      },
      body: JSON.stringify({ url })
    }),
    createUploadJob: (file) => {
      const formData = new FormData();
      formData.append("file", file);
      return postMultipart("/api/jobs/upload", formData);
    },
    approveCandidate: (id) => postJson(`/api/candidates/${id}/approve`),
    rejectCandidate: (id) => postJson(`/api/candidates/${id}/reject`),
    exportCandidate: (id) => postJson(`/api/candidates/${id}/export`)
  };

  const pages = {
    jobs: initJobsPage,
    job: initJobPage
  };

  const authState = {
    csrfToken: null,
    csrfPromise: null
  };

  const activeWorkerStatuses = new Set([
    "NEW",
    "QUEUED_FOR_DOWNLOAD",
    "DOWNLOADING",
    "QUEUED_FOR_PROCESSING",
    "EXTRACTING_AUDIO",
    "TRANSCRIBING",
    "DETECTING_SILENCE",
    "ANALYZING_WINDOWS",
    "GENERATING_CANDIDATES",
    "EXPORTING_CLIP"
  ]);

  const terminalJobStatuses = new Set(["COMPLETED", "FAILED", "CANCELED"]);

  const liveUpdates = {
    mode: null,
    timerId: null,
    inFlight: false,
    snapshot: "",
    root: null,
    jobId: null,
    interactionLock: false,
    interactionReason: ""
  };

  const liveIntervals = {
    fast: 4000,
    steady: 12000,
    hidden: 20000,
    paused: 3000,
    error: 6000
  };

  document.addEventListener("DOMContentLoaded", () => {
    const page = document.body.dataset.page;
    const init = pages[page];
    if (init) {
      init().catch((error) => {
        const target = document.querySelector("[data-page-root]");
        if (target) {
          target.innerHTML = renderError(error.message || "Unable to load page.");
        }
      });
    }
  });

  window.addEventListener("beforeunload", stopLiveUpdates);
  document.addEventListener("visibilitychange", () => {
    if (!document.hidden && liveUpdates.mode && liveUpdates.root?.isConnected) {
      scheduleLiveRefresh(250);
    }
  });

  async function initJobsPage() {
    const root = document.querySelector("[data-page-root]");
    root.innerHTML = renderLoading("Loading jobs...");

    const jobs = await renderJobsPage(root);
    startJobsLiveUpdates(root, jobs);
  }

  async function initJobPage() {
    const root = document.querySelector("[data-page-root]");
    const jobId = new URLSearchParams(window.location.search).get("id");

    if (!jobId) {
      root.innerHTML = renderError("Missing job id in the URL.");
      return;
    }

    root.innerHTML = renderLoading("Loading job details...");
    const pageData = await renderJobPage(root, jobId);
    startJobLiveUpdates(root, jobId, pageData);
  }

  async function renderJobPage(root, jobId, flashMessage = null, flashType = "info", pageData = null) {
    const { job, transcript, events, candidates } = pageData || await loadJobPageData(jobId);

    const candidateSummary = summarizeCandidates(candidates);

    root.innerHTML = `
      <div class="page-status" data-status-banner ${flashMessage ? "" : "hidden"}>
        ${flashMessage ? renderBanner(flashMessage, flashType) : ""}
      </div>
      <div class="breadcrumbs"><a href="/index.html">Jobs</a> / Job #${escapeHtml(job.id)}</div>
      ${renderJobFailureSummary(job, candidates)}
      <section class="panel">
        <div class="panel-header">
          <div>
            <span class="eyebrow">Job Details</span>
            <h2 style="margin-top: 12px; font-size: 1.6rem;">${escapeHtml(labelForJob(job))}</h2>
          </div>
          <div class="header-actions">
            <span class="live-indicator" data-live-indicator>Live updates booting...</span>
            <button class="action-button action-button-neutral" type="button" data-page-refresh>Refresh</button>
            <span class="pill ${statusClass(job.status)}">${escapeHtml(job.status)}</span>
          </div>
        </div>
        <div class="details-grid">
          <div class="info-list">
            ${infoItem("Source Type", job.sourceType)}
            ${infoItem("Source URL", job.sourceUrl || "n/a")}
            ${infoItem("Original File", job.originalFilename || "n/a")}
            ${infoItem("Created", formatDate(job.createdAt))}
            ${infoItem("Updated", formatDate(job.updatedAt))}
            ${infoItem("Duration", formatDuration(job.durationSec))}
            ${infoItem("Language", job.language || "n/a")}
          </div>
          <div class="info-list">
            ${infoItem("Started", formatDate(job.startedAt))}
            ${infoItem("Finished", formatDate(job.finishedAt))}
            ${infoItem("Storage Video Path", job.storageVideoPath || "n/a")}
            ${infoItem("Storage Audio Path", job.storageAudioPath || "n/a")}
            ${infoItem("Error", job.errorMessage || "none")}
          </div>
        </div>
      </section>

      ${renderWorkerRuntimePanel(job)}

      <section class="summary-grid">
        <article class="summary-card">
          <div class="count">${candidateSummary.total}</div>
          <div class="hint">Clip candidates</div>
        </article>
        <article class="summary-card">
          <div class="count">${candidateSummary.approved}</div>
          <div class="hint">Approved</div>
        </article>
        <article class="summary-card">
          <div class="count">${candidateSummary.pending}</div>
          <div class="hint">Pending review</div>
        </article>
      </section>

      <section class="details-grid">
        <div class="panel">
          <div class="panel-header">
            <div>
              <h2>Transcript Preview</h2>
              <p>Scan the first recovered segments and their coverage before you drill into clip candidates.</p>
            </div>
          </div>
          ${renderTranscript(transcript)}
        </div>
        <div class="panel">
          <div class="panel-header">
            <div>
              <h2>Job Events</h2>
              <p>A chronological feed of ingest, analysis, moderation, and export signals.</p>
            </div>
          </div>
          ${renderEvents(events)}
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <div>
            <h2>Candidate Review</h2>
            <p>Clip windows are ready to inspect immediately. Moderate only when you need curation, then download from the same surface.</p>
          </div>
        </div>
        ${renderCandidates(job, candidates)}
      </section>
    `;

    bindCandidateActions(root, jobId);
    bindJobPageActions(root, jobId);
    bindCandidatePreviewPlayers(root);
    syncLiveSnapshot("job", buildJobPageSnapshot(job, transcript, events, candidates), root, jobId);
    if (liveUpdates.mode === "job" && String(liveUpdates.jobId) === String(jobId)) {
      updateLiveIndicator(root, "live", describeJobLiveState(job, candidates));
    }
    return { job, transcript, events, candidates };
  }

  async function renderJobsPage(root, flashMessage = null, flashType = "info", jobsData = null) {
    const jobs = jobsData || await api.listJobs();
    const summary = summarizeJobs(jobs);

    root.innerHTML = `
      <div class="page-status" data-status-banner ${flashMessage ? "" : "hidden"}>
        ${flashMessage ? renderBanner(flashMessage, flashType) : ""}
      </div>
      <section class="panel">
        <div class="panel-header">
          <div>
            <h2>Create Job</h2>
            <p>Submit a VOD URL or upload a local file.</p>
          </div>
          <div class="header-actions">
            <span class="live-indicator" data-live-indicator>Live updates booting...</span>
            <button class="action-button action-button-neutral" type="button" data-jobs-refresh>Refresh List</button>
          </div>
        </div>
        <div class="control-grid">
          <form class="action-form" data-url-job-form>
            <label class="field-label" for="job-url-input">Create from URL</label>
            <div class="form-row">
              <input id="job-url-input" class="text-input" name="url" type="url" placeholder="https://example.com/video" required>
              <button class="action-button action-button-primary" type="submit">Create URL Job</button>
            </div>
            <div class="form-message" data-url-job-message></div>
          </form>
          <form class="action-form" data-upload-job-form>
            <label class="field-label" for="job-file-input">Create from File</label>
            <div class="form-row">
              <input id="job-file-input" class="file-input" name="file" type="file" accept=".mp4,.mov,.mkv,.webm,.avi,.mpeg,.mpg,video/mp4,video/quicktime,video/x-matroska,video/webm,video/x-msvideo,video/mpeg" required>
              <button class="action-button action-button-primary" type="submit">Upload Job</button>
            </div>
            <div class="upload-hint" aria-live="polite">
              Single file only. Supported formats: MP4, MOV, MKV, WEBM, AVI, MPEG/MPG.
              Max file size: 512 MB. Max request size: 520 MB.
            </div>
            <div class="form-message" data-upload-job-message></div>
          </form>
        </div>
      </section>
      <section class="stat-grid">
        <article class="stat-card"><span class="label">Jobs</span><span class="value">${summary.total}</span></article>
        <article class="stat-card"><span class="label">Active</span><span class="value">${summary.active}</span></article>
        <article class="stat-card"><span class="label">Ready</span><span class="value">${summary.ready}</span></article>
        <article class="stat-card"><span class="label">Finished</span><span class="value">${summary.finished}</span></article>
      </section>
      <section class="panel">
        <div class="panel-header">
          <div>
            <h2>Job List</h2>
            <p>Track jobs, source type, duration, and current status.</p>
          </div>
        </div>
        ${renderJobsTable(jobs)}
      </section>
    `;

    bindJobsPageActions(root);
    syncLiveSnapshot("jobs", buildJobsSnapshot(jobs), root);
    if (liveUpdates.mode === "jobs") {
      updateLiveIndicator(root, "live", describeJobsLiveState(jobs));
    }
    return jobs;
  }

  function bindJobsPageActions(root) {
    const refreshButton = root.querySelector("[data-jobs-refresh]");
    const urlForm = root.querySelector("[data-url-job-form]");
    const uploadForm = root.querySelector("[data-upload-job-form]");

    refreshButton?.addEventListener("click", async () => {
      setLiveInteractionLock(true, "Manual refresh in progress.");
      refreshButton.disabled = true;
      const previousLabel = refreshButton.textContent;
      refreshButton.textContent = "Refreshing...";
      try {
        await renderJobsPage(root, "Job list refreshed.", "info");
      } catch (error) {
        showInlineBanner(root, error.message || "Unable to refresh jobs.", "error");
        refreshButton.disabled = false;
        refreshButton.textContent = previousLabel;
      } finally {
        setLiveInteractionLock(false);
      }
    });

    urlForm?.addEventListener("submit", async (event) => {
      event.preventDefault();
      const submitButton = urlForm.querySelector("button[type='submit']");
      const input = urlForm.querySelector("input[name='url']");
      const message = urlForm.querySelector("[data-url-job-message]");
      const url = input.value.trim();

      if (!url) {
        setFormMessage(message, "URL is required.", "error");
        return;
      }

      setLiveInteractionLock(true, "URL job creation in progress.");
      submitButton.disabled = true;
      const previousLabel = submitButton.textContent;
      submitButton.textContent = "Creating...";
      setFormMessage(message, "Submitting URL job...", "info");

      try {
        const created = await api.createUrlJob(url);
        await renderJobsPage(root, `Job #${created.id} created from URL.`, "success");
      } catch (error) {
        setFormMessage(message, error.message || "Unable to create URL job.", "error");
        submitButton.disabled = false;
        submitButton.textContent = previousLabel;
      } finally {
        setLiveInteractionLock(false);
      }
    });

    uploadForm?.addEventListener("submit", async (event) => {
      event.preventDefault();
      const submitButton = uploadForm.querySelector("button[type='submit']");
      const input = uploadForm.querySelector("input[name='file']");
      const message = uploadForm.querySelector("[data-upload-job-message]");
      const file = input.files && input.files[0];

      if (!file) {
        setFormMessage(message, "Choose a file first.", "error");
        return;
      }

      setLiveInteractionLock(true, "File upload in progress.");
      submitButton.disabled = true;
      const previousLabel = submitButton.textContent;
      submitButton.textContent = "Uploading...";
      setFormMessage(message, `Uploading ${file.name}...`, "info");

      try {
        const created = await api.createUploadJob(file);
        await renderJobsPage(root, `Job #${created.id} created from file upload.`, "success");
      } catch (error) {
        setFormMessage(message, formatUploadErrorMessage(error), "error");
        submitButton.disabled = false;
        submitButton.textContent = previousLabel;
      } finally {
        setLiveInteractionLock(false);
      }
    });
  }

  function bindJobPageActions(root, jobId) {
    const refreshButton = root.querySelector("[data-page-refresh]");
    refreshButton?.addEventListener("click", async () => {
      setLiveInteractionLock(true, "Manual refresh in progress.");
      refreshButton.disabled = true;
      const previousLabel = refreshButton.textContent;
      refreshButton.textContent = "Refreshing...";
      try {
        await renderJobPage(root, jobId, `Job #${jobId} refreshed.`, "info");
      } catch (error) {
        showInlineBanner(root, error.message || "Unable to refresh job.", "error");
        refreshButton.disabled = false;
        refreshButton.textContent = previousLabel;
      } finally {
        setLiveInteractionLock(false);
      }
    });

    root.querySelectorAll("[data-job-control]").forEach((button) => {
      button.addEventListener("click", async () => {
        const action = button.dataset.jobControl;
        const actionLabel = action === "cancel" ? "Canceling..." : "Restarting...";
        const previousLabel = button.textContent;
        setLiveInteractionLock(true, `${previousLabel} in progress.`);
        button.disabled = true;
        button.textContent = actionLabel;

        try {
          if (action === "cancel") {
            await api.cancelJob(jobId);
            await renderJobPage(root, jobId, `Worker run for job #${jobId} canceled.`, "warning");
            return;
          }
          if (action === "restart") {
            await api.restartJob(jobId);
            await renderJobPage(root, jobId, `Worker run for job #${jobId} restarted.`, "success");
            return;
          }
          throw new Error("Unknown job control action.");
        } catch (error) {
          showInlineBanner(root, error.message || "Unable to update worker run.", "error");
          button.disabled = false;
          button.textContent = previousLabel;
        } finally {
          setLiveInteractionLock(false);
        }
      });
    });
  }

  function bindCandidateActions(root, jobId) {
    root.querySelectorAll("[data-candidate-action]").forEach((button) => {
      button.addEventListener("click", async (event) => {
        const candidateId = event.currentTarget.dataset.candidateId;
        const action = event.currentTarget.dataset.candidateAction;
        const candidateCard = event.currentTarget.closest("[data-candidate-card]");
        const message = candidateCard?.querySelector("[data-candidate-message]");
        const previousLabel = event.currentTarget.textContent;

        setLiveInteractionLock(true, "Candidate action in progress.");
        event.currentTarget.disabled = true;
        event.currentTarget.textContent = "Working...";
        setCardMessage(message, "Processing action...");

        try {
          let result;
          if (action === "approve") {
            result = await api.approveCandidate(candidateId);
            await renderJobPage(root, jobId, `Candidate #${result.id} approved.`, "success");
            return;
          }
          if (action === "reject") {
            result = await api.rejectCandidate(candidateId);
            await renderJobPage(root, jobId, `Candidate #${result.id} rejected.`, "warning");
            return;
          }
          if (action === "export") {
            result = await api.exportCandidate(candidateId);
            await renderJobPage(root, jobId, `Export started for candidate #${result.id}.`, "success");
            return;
          }
          if (action === "download") {
            const exportReady = event.currentTarget.dataset.exportReady === "true";
            await prepareCandidateDownload(candidateId, message, root, jobId, exportReady);
            return;
          }
          throw new Error("Unknown action.");
        } catch (error) {
          setCardMessage(message, error.message || "Action failed.");
          event.currentTarget.disabled = false;
          event.currentTarget.textContent = previousLabel;
          showInlineBanner(root, error.message || "Action failed.", "error");
        } finally {
          setLiveInteractionLock(false);
        }
      });
    });
  }

  async function loadJobPageData(jobId) {
    const [job, transcript, events, candidates] = await Promise.all([
      api.getJob(jobId),
      api.getTranscript(jobId),
      api.getEvents(jobId),
      api.getCandidates(jobId)
    ]);

    return { job, transcript, events, candidates };
  }

  function startJobsLiveUpdates(root, jobs) {
    stopLiveUpdates();
    liveUpdates.mode = "jobs";
    liveUpdates.root = root;
    liveUpdates.snapshot = buildJobsSnapshot(jobs);
    updateLiveIndicator(root, "live", describeJobsLiveState(jobs));
    scheduleLiveRefresh(computeJobsLiveDelay(jobs));
  }

  function startJobLiveUpdates(root, jobId, pageData) {
    stopLiveUpdates();
    liveUpdates.mode = "job";
    liveUpdates.root = root;
    liveUpdates.jobId = String(jobId);
    liveUpdates.snapshot = buildJobPageSnapshot(pageData.job, pageData.transcript, pageData.events, pageData.candidates);
    updateLiveIndicator(root, "live", describeJobLiveState(pageData.job, pageData.candidates));
    scheduleLiveRefresh(computeJobLiveDelay(pageData.job, pageData.candidates));
  }

  function stopLiveUpdates() {
    if (liveUpdates.timerId) {
      window.clearTimeout(liveUpdates.timerId);
    }
    liveUpdates.mode = null;
    liveUpdates.timerId = null;
    liveUpdates.inFlight = false;
    liveUpdates.snapshot = "";
    liveUpdates.root = null;
    liveUpdates.jobId = null;
    liveUpdates.interactionLock = false;
    liveUpdates.interactionReason = "";
  }

  function scheduleLiveRefresh(delay) {
    if (!liveUpdates.mode) {
      return;
    }
    if (liveUpdates.timerId) {
      window.clearTimeout(liveUpdates.timerId);
    }
    liveUpdates.timerId = window.setTimeout(() => {
      if (liveUpdates.mode === "jobs") {
        void runJobsLiveRefresh();
        return;
      }
      if (liveUpdates.mode === "job") {
        void runJobLiveRefresh();
      }
    }, delay);
  }

  async function runJobsLiveRefresh() {
    if (liveUpdates.mode !== "jobs" || !liveUpdates.root?.isConnected) {
      return;
    }
    if (liveUpdates.inFlight) {
      scheduleLiveRefresh(liveIntervals.paused);
      return;
    }

    const root = liveUpdates.root;
    const initialPauseReason = getJobsLivePauseReason(root);
    if (initialPauseReason) {
      updateLiveIndicator(root, "paused", initialPauseReason);
      scheduleLiveRefresh(liveIntervals.paused);
      return;
    }

    liveUpdates.inFlight = true;
    let jobs = null;

    try {
      jobs = await api.listJobs();
      const snapshot = buildJobsSnapshot(jobs);
      const currentPauseReason = getJobsLivePauseReason(root);
      if (currentPauseReason) {
        updateLiveIndicator(root, "paused", currentPauseReason);
      } else if (snapshot !== liveUpdates.snapshot) {
        await renderJobsPage(root, null, "info", jobs);
        updateLiveIndicator(root, "live", `Worker activity detected. ${describeJobsLiveState(jobs)}`);
      } else {
        updateLiveIndicator(root, "live", describeJobsLiveState(jobs));
      }
    } catch (error) {
      updateLiveIndicator(root, "error", `Live updates stalled: ${error.message || "request failed"}`);
      scheduleLiveRefresh(liveIntervals.error);
      liveUpdates.inFlight = false;
      return;
    }

    liveUpdates.inFlight = false;
    scheduleLiveRefresh(computeJobsLiveDelay(jobs || []));
  }

  async function runJobLiveRefresh() {
    if (liveUpdates.mode !== "job" || !liveUpdates.root?.isConnected) {
      return;
    }
    if (liveUpdates.inFlight) {
      scheduleLiveRefresh(liveIntervals.paused);
      return;
    }

    const root = liveUpdates.root;
    const initialPauseReason = getJobLivePauseReason(root);
    if (initialPauseReason) {
      updateLiveIndicator(root, "paused", initialPauseReason);
      scheduleLiveRefresh(liveIntervals.paused);
      return;
    }

    liveUpdates.inFlight = true;
    let pageData = null;

    try {
      pageData = await loadJobPageData(liveUpdates.jobId);
      const snapshot = buildJobPageSnapshot(pageData.job, pageData.transcript, pageData.events, pageData.candidates);
      const currentPauseReason = getJobLivePauseReason(root);
      if (currentPauseReason) {
        updateLiveIndicator(root, "paused", currentPauseReason);
      } else if (snapshot !== liveUpdates.snapshot) {
        await renderJobPage(root, liveUpdates.jobId, null, "info", pageData);
        updateLiveIndicator(root, "live", `Worker activity detected. ${describeJobLiveState(pageData.job, pageData.candidates)}`);
      } else {
        updateLiveIndicator(root, "live", describeJobLiveState(pageData.job, pageData.candidates));
      }
    } catch (error) {
      updateLiveIndicator(root, "error", `Live updates stalled: ${error.message || "request failed"}`);
      scheduleLiveRefresh(liveIntervals.error);
      liveUpdates.inFlight = false;
      return;
    }

    liveUpdates.inFlight = false;
    scheduleLiveRefresh(computeJobLiveDelay(pageData.job, pageData.candidates));
  }

  function buildJobsSnapshot(jobs) {
    return JSON.stringify((jobs || []).map((job) => [
      job.id,
      job.status,
      job.progressPercent,
      job.progressMessage,
      job.updatedAt,
      job.currentWorkerId
    ]));
  }

  function buildJobPageSnapshot(job, transcript, events, candidates) {
    return JSON.stringify({
      job: {
        id: job?.id,
        status: job?.status,
        progressPercent: job?.progressPercent,
        progressMessage: job?.progressMessage,
        currentWorkerId: job?.currentWorkerId,
        lastWorkerHeartbeatAt: job?.lastWorkerHeartbeatAt,
        updatedAt: job?.updatedAt,
        finishedAt: job?.finishedAt,
        errorMessage: job?.errorMessage
      },
      transcript: (transcript || []).map((segment) => [
        segment.startSec,
        segment.endSec,
        segment.text
      ]),
      events: (events || []).map((event) => [
        event.id,
        event.eventType,
        event.createdAt,
        event.message
      ]),
      candidates: (candidates || []).map((candidate) => [
        candidate.id,
        candidate.moderationStatus,
        candidate.exportStatus,
        candidate.exportReady,
        candidate.moderatorNote
      ])
    });
  }

  function syncLiveSnapshot(mode, snapshot, root, jobId = null) {
    if (liveUpdates.mode !== mode || liveUpdates.root !== root) {
      return;
    }
    if (mode === "job" && String(liveUpdates.jobId) !== String(jobId)) {
      return;
    }
    liveUpdates.snapshot = snapshot;
  }

  function setLiveInteractionLock(locked, reason = "") {
    liveUpdates.interactionLock = locked;
    liveUpdates.interactionReason = locked ? reason : "";
  }

  function getJobsLivePauseReason(root) {
    if (liveUpdates.interactionLock) {
      return liveUpdates.interactionReason || "Live updates paused while work is in progress.";
    }

    const fileInput = root.querySelector("input[name='file']");
    if (fileInput?.files?.length) {
      return "Live updates paused while a file is selected for upload.";
    }

    const urlInput = root.querySelector("input[name='url']");
    if (urlInput && urlInput.value.trim()) {
      return "Live updates paused while the URL form has unsaved input.";
    }

    const activeElement = document.activeElement;
    if (activeElement && root.contains(activeElement) && ["INPUT", "TEXTAREA", "SELECT"].includes(activeElement.tagName)) {
      return "Live updates paused while you are editing the form.";
    }

    return null;
  }

  function getJobLivePauseReason(root) {
    if (liveUpdates.interactionLock) {
      return liveUpdates.interactionReason || "Live updates paused while work is in progress.";
    }

    if (document.fullscreenElement && root.contains(document.fullscreenElement)) {
      return "Live updates paused while video is in fullscreen.";
    }

    const previewPlaying = Array.from(root.querySelectorAll("[data-preview-video]"))
      .some((video) => !video.paused || video.seeking);
    if (previewPlaying) {
      return "Live updates paused while clip preview is playing.";
    }

    return null;
  }

  function computeJobsLiveDelay(jobs) {
    if (document.hidden) {
      return liveIntervals.hidden;
    }
    return hasBackgroundActiveJobs(jobs) ? liveIntervals.fast : liveIntervals.steady;
  }

  function computeJobLiveDelay(job, candidates) {
    if (document.hidden) {
      return liveIntervals.hidden;
    }
    return hasBackgroundActiveJob(job, candidates) ? liveIntervals.fast : liveIntervals.steady;
  }

  function hasBackgroundActiveJobs(jobs) {
    return (jobs || []).some((job) => activeWorkerStatuses.has(String(job.status || "").toUpperCase()));
  }

  function hasBackgroundActiveJob(job, candidates) {
    const status = String(job?.status || "").toUpperCase();
    if (activeWorkerStatuses.has(status)) {
      return true;
    }
    return (candidates || []).some((candidate) => String(candidate.exportStatus || "").toUpperCase() === "IN_PROGRESS");
  }

  function describeJobsLiveState(jobs) {
    const activeCount = (jobs || []).filter((job) => activeWorkerStatuses.has(String(job.status || "").toUpperCase())).length;
    if (activeCount > 0) {
      return `Watching ${activeCount} active job${activeCount === 1 ? "" : "s"} for worker updates. Last check ${formatLiveClock()}.`;
    }
    return `Watching the queue for new activity. Last check ${formatLiveClock()}.`;
  }

  function describeJobLiveState(job, candidates) {
    if (hasBackgroundActiveJob(job, candidates)) {
      return `Watching this job for worker updates. Last check ${formatLiveClock()}.`;
    }
    if (!terminalJobStatuses.has(String(job?.status || "").toUpperCase())) {
      return `Watching this job for moderation changes. Last check ${formatLiveClock()}.`;
    }
    return `Watching this job at a low cadence. Last check ${formatLiveClock()}.`;
  }

  function updateLiveIndicator(root, state, message) {
    const indicator = root.querySelector("[data-live-indicator]");
    if (!indicator) {
      return;
    }
    indicator.dataset.state = state;
    indicator.textContent = message;
  }

  function formatLiveClock() {
    return new Intl.DateTimeFormat(undefined, {
      timeStyle: "short"
    }).format(new Date());
  }

  function renderWorkerRuntimePanel(job) {
    const progressPercent = normalizedProgressPercent(job);
    const currentStatus = String(job?.status || "").toUpperCase();
    const canCancel = isJobCancelable(job);
    const canRestart = isJobRestartable(job);
    const progressLabel = job?.progressMessage || defaultProgressMessage(job);
    const phaseLabel = formatEventType(currentStatus);
    const heartbeatLabel = formatWorkerHeartbeat(job?.lastWorkerHeartbeatAt);
    const workerLabel = job?.currentWorkerId || "Awaiting worker claim";

    return `
      <section class="panel worker-runtime-panel">
        <div class="panel-header">
          <div>
            <span class="eyebrow">Worker Runtime</span>
            <h2 style="margin-top: 12px; font-size: 1.45rem;">${escapeHtml(phaseLabel)}</h2>
            <p>${escapeHtml(progressLabel)}</p>
          </div>
          <div class="header-actions">
            ${canRestart ? '<button class="action-button action-button-primary" type="button" data-job-control="restart">Restart Worker Run</button>' : ""}
            ${canCancel ? '<button class="action-button action-button-reject" type="button" data-job-control="cancel">Cancel Worker Run</button>' : ""}
          </div>
        </div>
        <div class="worker-progress-shell ${activeWorkerStatuses.has(currentStatus) ? "is-active" : ""}">
          <div class="worker-progress-meta">
            <span class="worker-progress-pill">${escapeHtml(`${progressPercent}%`)}</span>
            <span class="worker-progress-copy">${escapeHtml(progressLabel)}</span>
          </div>
          <div class="worker-progress-track" aria-hidden="true">
            <div class="worker-progress-fill" style="width: ${escapeHtml(progressPercent)}%;"></div>
            <div class="worker-progress-sheen"></div>
            <div class="worker-progress-grid"></div>
          </div>
          <div class="worker-runtime-grid">
            <article class="micro-card">
              <span class="micro-card-label">Worker</span>
              <strong class="micro-card-value micro-card-value-compact">${escapeHtml(workerLabel)}</strong>
            </article>
            <article class="micro-card">
              <span class="micro-card-label">Last Heartbeat</span>
              <strong class="micro-card-value micro-card-value-compact">${escapeHtml(heartbeatLabel)}</strong>
            </article>
            <article class="micro-card">
              <span class="micro-card-label">Execution</span>
              <strong class="micro-card-value micro-card-value-compact">v${escapeHtml(job?.processingVersion ?? "n/a")}</strong>
            </article>
          </div>
          <div class="footer-note">${escapeHtml(workerActionHint(job))}</div>
        </div>
      </section>
    `;
  }

  function normalizedProgressPercent(job) {
    const rawValue = Number(job?.progressPercent);
    if (!Number.isNaN(rawValue) && rawValue >= 0) {
      return Math.max(0, Math.min(Math.round(rawValue), 100));
    }

    const fallbackByStatus = {
      NEW: 0,
      QUEUED_FOR_DOWNLOAD: 5,
      DOWNLOADING: 18,
      QUEUED_FOR_PROCESSING: 28,
      EXTRACTING_AUDIO: 36,
      TRANSCRIBING: 48,
      DETECTING_SILENCE: 68,
      ANALYZING_WINDOWS: 84,
      GENERATING_CANDIDATES: 94,
      EXPORTING_CLIP: 92,
      READY_FOR_REVIEW: 100,
      COMPLETED: 100,
      FAILED: 0,
      CANCELED: 0
    };
    return fallbackByStatus[String(job?.status || "").toUpperCase()] ?? 0;
  }

  function defaultProgressMessage(job) {
    const byStatus = {
      NEW: "Job has been created but not queued yet.",
      QUEUED_FOR_DOWNLOAD: "Job is queued for the download worker.",
      DOWNLOADING: "Download worker is materializing the source video.",
      QUEUED_FOR_PROCESSING: "Source video is ready and queued for the processing worker.",
      EXTRACTING_AUDIO: "Processing worker is extracting the audio track.",
      TRANSCRIBING: "Worker is transcribing the audio.",
      DETECTING_SILENCE: "Worker is detecting silence spans.",
      ANALYZING_WINDOWS: "Worker is scoring sliding windows.",
      GENERATING_CANDIDATES: "Worker is assembling clip candidates.",
      READY_FOR_REVIEW: "Analysis finished. Candidates are ready for review.",
      EXPORTING_CLIP: "Worker is exporting the approved clip.",
      COMPLETED: "Worker run finished successfully.",
      FAILED: job?.errorMessage || "Worker run failed.",
      CANCELED: "Worker run was canceled by an operator."
    };
    return byStatus[String(job?.status || "").toUpperCase()] || "Worker state is unavailable.";
  }

  function formatWorkerHeartbeat(value) {
    if (!value) {
      return "No heartbeat recorded";
    }
    return formatDate(value);
  }

  function isJobCancelable(job) {
    const status = String(job?.status || "").toUpperCase();
    return ["QUEUED_FOR_DOWNLOAD", "DOWNLOADING", "QUEUED_FOR_PROCESSING", "EXTRACTING_AUDIO", "TRANSCRIBING", "DETECTING_SILENCE", "ANALYZING_WINDOWS", "GENERATING_CANDIDATES", "EXPORTING_CLIP"].includes(status);
  }

  function isJobRestartable(job) {
    const status = String(job?.status || "").toUpperCase();
    return ["QUEUED_FOR_DOWNLOAD", "DOWNLOADING", "QUEUED_FOR_PROCESSING", "EXTRACTING_AUDIO", "TRANSCRIBING", "DETECTING_SILENCE", "ANALYZING_WINDOWS", "GENERATING_CANDIDATES", "EXPORTING_CLIP", "FAILED", "CANCELED"].includes(status);
  }

  function workerActionHint(job) {
    const status = String(job?.status || "").toUpperCase();
    if (status === "FAILED") {
      return "Restart the worker run after checking the failure reason in the event feed.";
    }
    if (status === "CANCELED") {
      return "This run was canceled manually. Restart to requeue it through the download stage.";
    }
    if (isJobCancelable(job)) {
      return "Cancel stops the current run immediately. Restart invalidates the current worker lease and queues a fresh attempt.";
    }
    return "No operator recovery action is available for the current state.";
  }

  function renderCandidates(job, candidates) {
    if (!candidates.length) {
      return `<div class="empty-state">No candidates available yet.</div>`;
    }

    return `
      <div class="stack">
        ${candidates.map((candidate) => `
          <article class="candidate-card" data-candidate-card>
            <div class="candidate-preview-shell" data-preview-shell data-preview-state="loading">
              <div class="candidate-preview-head">
                <span class="candidate-preview-chip">Instant preview</span>
                <span class="candidate-preview-chip candidate-preview-chip-muted">${timeRange(candidate.startSec, candidate.endSec)}</span>
              </div>
              <div class="candidate-preview-stage">
                <video
                  class="candidate-preview"
                  controls
                  preload="metadata"
                  playsinline
                  data-preview-video
                  data-preview-start-sec="${escapeHtml(candidate.startSec)}"
                  data-preview-end-sec="${escapeHtml(candidate.endSec)}"
                  src="/api/jobs/${encodeURIComponent(job.id)}/source/stream"></video>
              </div>
              <div class="candidate-preview-foot">
                <div class="candidate-preview-progress" aria-hidden="true">
                  <span class="candidate-preview-progress-bar"></span>
                </div>
                <div class="candidate-preview-note" data-preview-note>Loading clip window...</div>
              </div>
            </div>
            <div class="candidate-top">
              <div>
                <strong>${timeRange(candidate.startSec, candidate.endSec)}</strong>
                <div class="muted">Score ${formatScore(candidate.score)}</div>
              </div>
              <span class="pill ${statusClass(candidate.moderationStatus)}">${escapeHtml(candidate.moderationStatus)}</span>
            </div>
            <div class="candidate-excerpt">${escapeHtml(candidate.transcriptExcerpt || "No transcript excerpt available.")}</div>
            <div class="candidate-meta">
              <span>${escapeHtml(candidate.moderatorNote || "No moderator note yet.")}</span>
              <span>${candidate.exportReady
                ? `<a href="/api/exports/${encodeURIComponent(candidate.id)}/file">Download clip</a>`
                : (candidate.exportStatus === "IN_PROGRESS" ? "Clip is being prepared" : "Click download to prepare the clip")}</span>
            </div>
            ${renderCandidateRuntimeState(candidate)}
            <div class="candidate-actions">
              <button class="action-button action-button-approve" type="button" data-candidate-action="approve" data-candidate-id="${escapeHtml(candidate.id)}" ${candidate.moderationStatus === "APPROVED" ? "disabled" : ""}>Approve</button>
              <button class="action-button action-button-reject" type="button" data-candidate-action="reject" data-candidate-id="${escapeHtml(candidate.id)}" ${candidate.moderationStatus === "REJECTED" ? "disabled" : ""}>Reject</button>
              <button class="action-button action-button-export" type="button" data-candidate-action="download" data-candidate-id="${escapeHtml(candidate.id)}" data-export-ready="${candidate.exportReady}" ${candidate.exportStatus === "IN_PROGRESS" ? "disabled" : ""}>Download</button>
            </div>
            <div class="candidate-message" data-candidate-message></div>
          </article>
        `).join("")}
      </div>
    `;
  }

  function setCardMessage(target, message) {
    if (!target) {
      return;
    }
    target.textContent = message;
    target.dataset.state = "info";
  }

  function showInlineBanner(root, message, level) {
    const banner = root.querySelector("[data-status-banner]");
    if (!banner) {
      return;
    }
    banner.hidden = false;
    banner.innerHTML = renderBanner(message, level);
  }

  function setFormMessage(target, message, level) {
    if (!target) {
      return;
    }
    target.textContent = message;
    target.dataset.state = level || "info";
  }

  function formatUploadErrorMessage(error) {
    const baseMessage = error?.message || "Unable to upload job.";
    if (baseMessage.includes("Upload exceeds the")) {
      return `${baseMessage} Choose a smaller supported file and try again.`;
    }
    return baseMessage;
  }

  function renderBanner(message, level) {
    return `<div class="banner banner-${level}">${escapeHtml(message)}</div>`;
  }

  function renderJobFailureSummary(job, candidates) {
    if (job.status === "CANCELED") {
      return `
        <section class="failure-summary">
          <div class="failure-summary-kicker">Worker canceled</div>
          <h2>Job run was stopped manually</h2>
          <p>${escapeHtml(job.errorMessage || "The current worker run was canceled by an operator.")}</p>
        </section>
      `;
    }

    if (job.status !== "FAILED" || !job.errorMessage) {
      return "";
    }

    const failureKind = candidates.some((candidate) => candidate.exportStatus === "FAILED")
      ? "Export failure"
      : "Ingest failure";

    return `
      <section class="failure-summary">
        <div class="failure-summary-kicker">${escapeHtml(failureKind)}</div>
        <h2>Job requires attention</h2>
        <p>${escapeHtml(job.errorMessage)}</p>
      </section>
    `;
  }

  function renderCandidateRuntimeState(candidate) {
    if (candidate.exportReady) {
      return `
        <div class="runtime-state runtime-state-ready">
          <span class="runtime-dot"></span>
          <span>Clip file is ready to download.</span>
        </div>
      `;
    }

    if (candidate.exportStatus === "IN_PROGRESS") {
      return `
        <div class="runtime-progress" aria-label="Export in progress">
          <div class="runtime-progress-head">
            <div class="runtime-spinner" aria-hidden="true"></div>
            <span>Worker is preparing this clip for download</span>
          </div>
          <div class="runtime-progress-track" aria-hidden="true">
            <div class="runtime-progress-bar"></div>
          </div>
        </div>
      `;
    }

    return "";
  }

  function bindCandidatePreviewPlayers(root) {
    root.querySelectorAll("[data-preview-video]").forEach((video) => {
      const shell = video.closest("[data-preview-shell]");
      const note = shell?.querySelector("[data-preview-note]");
      const startSec = Number(video.dataset.previewStartSec || "0");
      const endSec = Number(video.dataset.previewEndSec || "0");
      if (Number.isNaN(startSec) || Number.isNaN(endSec) || endSec <= startSec) {
        if (shell) {
          shell.dataset.previewState = "error";
        }
        if (note) {
          note.textContent = "Preview window is unavailable for this candidate.";
        }
        return;
      }

      const clipStart = Math.max(0, startSec + 0.01);
      const clipDuration = Math.max(endSec - startSec, 0.01);
      const readyMessage = "Press play to inspect this clip window. Scrubbing stays inside the selected range.";
      const playingMessage = "Playback is constrained to this clip window.";
      const pausedMessage = "Preview paused inside the clip window.";
      const replayMessage = "Clip window finished. Press play to replay it from the start.";
      const clampedMessage = "Playback is limited to the selected clip window.";
      let internalPause = false;

      const setPreviewState = (state, message) => {
        if (shell) {
          shell.dataset.previewState = state;
        }
        if (note) {
          note.textContent = message;
        }
      };

      const updateProgress = () => {
        const progress = Math.max(0, Math.min((video.currentTime - startSec) / clipDuration, 1));
        if (shell) {
          shell.style.setProperty("--preview-progress", `${Math.round(progress * 100)}%`);
        }
      };

      const seekToClipStart = (force = false) => {
        try {
          if (force || video.currentTime < startSec || video.currentTime > endSec || Math.abs(video.currentTime - clipStart) > 0.05) {
            video.currentTime = clipStart;
          }
        } catch (error) {
          void error;
        }
        updateProgress();
      };

      const clampToClipWindow = () => {
        if (video.currentTime < startSec) {
          seekToClipStart(true);
          return true;
        }
        if (video.currentTime > endSec) {
          seekToClipStart(true);
          return true;
        }
        updateProgress();
        return false;
      };

      setPreviewState("loading", "Loading clip window...");
      video.addEventListener("loadedmetadata", () => {
        seekToClipStart(true);
        setPreviewState("ready", readyMessage);
      });
      video.addEventListener("loadeddata", () => {
        seekToClipStart(true);
        setPreviewState("ready", readyMessage);
      });
      video.addEventListener("canplay", () => {
        setPreviewState("ready", readyMessage);
      });
      video.addEventListener("play", () => {
        clampToClipWindow();
        setPreviewState("playing", playingMessage);
      });
      video.addEventListener("pause", () => {
        if (internalPause) {
          return;
        }
        setPreviewState("paused", pausedMessage);
      });
      video.addEventListener("seeking", () => {
        if (clampToClipWindow()) {
          setPreviewState("paused", clampedMessage);
        }
      });
      video.addEventListener("timeupdate", () => {
        updateProgress();
        if (video.currentTime >= endSec - 0.05) {
          internalPause = true;
          video.pause();
          internalPause = false;
          seekToClipStart(true);
          setPreviewState("ready", replayMessage);
        }
      });
      video.addEventListener("ended", () => {
        seekToClipStart(true);
        setPreviewState("ready", replayMessage);
      });
      video.addEventListener("error", () => {
        if (shell) {
          shell.dataset.previewState = "error";
        }
        if (note) {
          note.textContent = "Preview could not be loaded. Refresh the page and try again.";
        }
      });
    });
  }

  async function prepareCandidateDownload(candidateId, message, root, jobId, exportReady) {
    if (exportReady) {
      window.location.href = `/api/exports/${encodeURIComponent(candidateId)}/file`;
      return;
    }

    setCardMessage(message, "Preparing clip for download...");

    let exportState = await api.exportCandidate(candidateId);
    if (exportState.exportReady) {
      window.location.href = `/api/exports/${encodeURIComponent(candidateId)}/file`;
      await renderJobPage(root, jobId, `Clip for candidate #${candidateId} downloaded.`, "success");
      return;
    }

    for (let attempt = 0; attempt < 20; attempt += 1) {
      await wait(1500);
      exportState = await fetchJson(`/api/exports/${encodeURIComponent(candidateId)}`);
      if (exportState.exportReady) {
        window.location.href = `/api/exports/${encodeURIComponent(candidateId)}/file`;
        await renderJobPage(root, jobId, `Clip for candidate #${candidateId} is ready.`, "success");
        return;
      }
      if (String(exportState.status || "").toUpperCase() === "FAILED") {
        throw new Error("Clip export failed. Check the job failure reason and retry.");
      }
    }

    throw new Error("Clip is still being prepared. Refresh the page and try download again.");
  }

  function wait(ms) {
    return new Promise((resolve) => {
      window.setTimeout(resolve, ms);
    });
  }

  function renderJobsTable(jobs) {
    if (!jobs.length) {
      return `<div class="empty-state">No jobs yet. Submit a URL or upload a file to create the first one.</div>`;
    }

    const rows = jobs.map((job) => `
      <tr>
        <td><a href="/job.html?id=${encodeURIComponent(job.id)}">Job #${escapeHtml(job.id)}</a></td>
        <td><span class="pill ${statusClass(job.status)}">${escapeHtml(job.status)}</span></td>
        <td>${renderCompactProgress(job)}</td>
        <td>${escapeHtml(sourceLabel(job))}</td>
        <td>${escapeHtml(formatDate(job.createdAt))}</td>
        <td>${escapeHtml(formatDuration(job.durationSec))}</td>
      </tr>
    `).join("");

    return `
      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Job</th>
              <th>Status</th>
              <th>Worker</th>
              <th>Source</th>
              <th>Created</th>
              <th>Duration</th>
            </tr>
          </thead>
          <tbody>${rows}</tbody>
        </table>
      </div>
    `;
  }

  function renderCompactProgress(job) {
    const progressPercent = normalizedProgressPercent(job);
    const progressLabel = job?.progressMessage || defaultProgressMessage(job);
    return `
      <div class="table-progress">
        <div class="table-progress-copy">${escapeHtml(progressLabel)}</div>
        <div class="table-progress-track" aria-hidden="true">
          <span class="table-progress-fill" style="width: ${escapeHtml(progressPercent)}%;"></span>
        </div>
        <div class="table-progress-meta">${escapeHtml(`${progressPercent}%`)}${job?.currentWorkerId ? ` · ${escapeHtml(job.currentWorkerId)}` : ""}</div>
      </div>
    `;
  }

  function renderTranscript(transcript) {
    if (!transcript.length) {
      return `<div class="empty-state">Transcript is empty or not ready yet.</div>`;
    }

    const visibleSegments = transcript.slice(0, 8);
    const firstSegment = visibleSegments[0];
    const lastSegment = visibleSegments[visibleSegments.length - 1];

    return `
      <div class="panel-micro-grid">
        <article class="micro-card">
          <span class="micro-card-label">Visible</span>
          <strong class="micro-card-value">${visibleSegments.length}/${transcript.length}</strong>
        </article>
        <article class="micro-card">
          <span class="micro-card-label">Coverage</span>
          <strong class="micro-card-value micro-card-value-compact">${timeRange(firstSegment.startSec, lastSegment.endSec)}</strong>
        </article>
      </div>
      <div class="transcript-preview">
        ${visibleSegments.map((segment, index) => `
          <article class="transcript-line">
            <div class="transcript-line-meta">
              <span class="transcript-index">S${String(index + 1).padStart(2, "0")}</span>
              <span class="transcript-time">${timeRange(segment.startSec, segment.endSec)}</span>
            </div>
            <div class="transcript-copy">${escapeHtml(segment.text || "")}</div>
          </article>
        `).join("")}
      </div>
      <div class="footer-note">Showing ${visibleSegments.length} of ${transcript.length} segments.</div>
    `;
  }

  function renderEvents(events) {
    if (!events.length) {
      return `<div class="empty-state">No job events recorded yet.</div>`;
    }

    const latestEvent = events[events.length - 1];

    return `
      <div class="panel-micro-grid">
        <article class="micro-card">
          <span class="micro-card-label">Recorded</span>
          <strong class="micro-card-value">${events.length}</strong>
        </article>
        <article class="micro-card">
          <span class="micro-card-label">Latest</span>
          <strong class="micro-card-value micro-card-value-compact">${escapeHtml(formatEventType(latestEvent.eventType))}</strong>
        </article>
      </div>
      <div class="event-feed">
        ${events.map((event, index) => `
          <article class="event-row">
            <div class="event-rail" aria-hidden="true">
              <span class="event-node ${eventToneClass(event.eventType)}"></span>
              ${index === events.length - 1 ? "" : '<span class="event-rail-line"></span>'}
            </div>
            <div class="event-card ${eventToneClass(event.eventType)}">
              <div class="event-meta">
                <span class="event-type-pill ${eventToneClass(event.eventType)}">${escapeHtml(formatEventType(event.eventType))}</span>
                <span class="event-time">${escapeHtml(formatDate(event.createdAt))}</span>
              </div>
              <p class="event-message">${escapeHtml(event.message || "No event message recorded.")}</p>
            </div>
          </article>
        `).join("")}
      </div>
    `;
  }

  function summarizeJobs(jobs) {
    const activeStatuses = new Set(["NEW", "QUEUED_FOR_DOWNLOAD", "DOWNLOADING", "QUEUED_FOR_PROCESSING", "EXTRACTING_AUDIO", "TRANSCRIBING", "DETECTING_SILENCE", "ANALYZING_WINDOWS", "GENERATING_CANDIDATES", "READY_FOR_REVIEW", "EXPORTING_CLIP"]);
    const readyStatuses = new Set(["READY_FOR_REVIEW"]);
    const finishedStatuses = new Set(["COMPLETED", "FAILED", "CANCELED"]);
    return jobs.reduce((acc, job) => {
      acc.total += 1;
      if (activeStatuses.has(job.status)) {
        acc.active += 1;
      }
      if (readyStatuses.has(job.status)) {
        acc.ready += 1;
      }
      if (finishedStatuses.has(job.status)) {
        acc.finished += 1;
      }
      return acc;
    }, { total: 0, active: 0, ready: 0, finished: 0 });
  }

  function summarizeCandidates(candidates) {
    return candidates.reduce((acc, candidate) => {
      acc.total += 1;
      if (candidate.moderationStatus === "APPROVED") {
        acc.approved += 1;
      }
      if (candidate.moderationStatus === "PENDING") {
        acc.pending += 1;
      }
      return acc;
    }, { total: 0, approved: 0, pending: 0 });
  }

  function sourceLabel(job) {
    if (job.sourceType === "FILE") {
      return job.originalFilename || "Uploaded file";
    }
    return job.sourceUrl || "URL";
  }

  function labelForJob(job) {
    return job.sourceType === "FILE"
      ? (job.originalFilename || `Job ${job.id}`)
      : (job.sourceUrl || `Job ${job.id}`);
  }

  function infoItem(label, value) {
    return `
      <div class="info-item">
        <div class="label">${escapeHtml(label)}</div>
        <div class="value">${escapeHtml(value || "n/a")}</div>
      </div>
    `;
  }

  function statusClass(status) {
    return `status-${String(status || "").toLowerCase()}`;
  }

  function formatEventType(eventType) {
    const value = String(eventType || "").trim();
    if (!value) {
      return "Event";
    }
    return value
      .toLowerCase()
      .split("_")
      .map((part) => part ? `${part.charAt(0).toUpperCase()}${part.slice(1)}` : "")
      .join(" ");
  }

  function eventToneClass(eventType) {
    const value = String(eventType || "").toUpperCase();
    if (value.includes("FAILED") || value.includes("REJECTED")) {
      return "tone-danger";
    }
    if (value.includes("READY") || value.includes("COMPLETED")) {
      return "tone-success";
    }
    if (value.includes("QUEUED") || value.includes("CLAIMED") || value.includes("EXPORT") || value.includes("STARTED")) {
      return "tone-accent";
    }
    return "tone-muted";
  }

  function formatScore(score) {
    if (score === null || score === undefined || Number.isNaN(Number(score))) {
      return "n/a";
    }
    return Number(score).toFixed(2);
  }

  function formatDuration(durationSec) {
    if (durationSec === null || durationSec === undefined) {
      return "n/a";
    }
    const total = Number(durationSec);
    if (Number.isNaN(total)) {
      return "n/a";
    }
    const minutes = Math.floor(total / 60);
    const seconds = Math.floor(total % 60);
    if (minutes === 0) {
      return `${seconds}s`;
    }
    return `${minutes}m ${seconds}s`;
  }

  function formatDate(value) {
    if (!value) {
      return "n/a";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return String(value);
    }
    return new Intl.DateTimeFormat(undefined, {
      dateStyle: "medium",
      timeStyle: "short"
    }).format(date);
  }

  function formatClipTimestamp(seconds) {
    if (seconds === null || seconds === undefined) {
      return "n/a";
    }

    const total = Number(seconds);
    if (Number.isNaN(total)) {
      return String(seconds);
    }

    const safeTotal = Math.max(0, total);
    const hours = Math.floor(safeTotal / 3600);
    const minutes = Math.floor((safeTotal % 3600) / 60);
    const secondsPart = safeTotal % 60;
    const wholeSeconds = Math.floor(secondsPart);
    const tenths = Math.floor((secondsPart - wholeSeconds) * 10);
    const secondLabel = `${String(wholeSeconds).padStart(2, "0")}.${tenths}`;

    if (hours > 0) {
      return `${String(hours).padStart(2, "0")}:${String(minutes).padStart(2, "0")}:${secondLabel}`;
    }

    return `${String(minutes).padStart(2, "0")}:${secondLabel}`;
  }

  function timeRange(startSec, endSec) {
    if (startSec === null || startSec === undefined || endSec === null || endSec === undefined) {
      return "n/a";
    }
    return `${formatClipTimestamp(startSec)} - ${formatClipTimestamp(endSec)}`;
  }

  async function fetchJson(url) {
    const response = await fetch(url, {
      headers: {
        Accept: "application/json"
      }
    });
    if (handleAuthFailure(response)) {
      throw new Error("Authentication required.");
    }
    if (!response.ok) {
      throw new Error(await readErrorMessage(response));
    }
    return response.json();
  }

  async function postJson(url, options = {}) {
    const csrfToken = await getCsrfToken();
    const response = await fetch(url, {
      method: "POST",
      headers: {
        Accept: "application/json",
        "X-XSRF-TOKEN": csrfToken,
        ...(options.headers || {})
      },
      body: options.body
    });
    if (handleAuthFailure(response)) {
      throw new Error("Authentication required.");
    }
    if (!response.ok) {
      throw new Error(await readErrorMessage(response));
    }
    return response.json();
  }

  async function postMultipart(url, formData) {
    const csrfToken = await getCsrfToken();
    const response = await fetch(url, {
      method: "POST",
      headers: {
        Accept: "application/json",
        "X-XSRF-TOKEN": csrfToken
      },
      body: formData
    });
    if (handleAuthFailure(response)) {
      throw new Error("Authentication required.");
    }
    if (!response.ok) {
      throw new Error(await readErrorMessage(response));
    }
    return response.json();
  }

  async function getCsrfToken() {
    if (authState.csrfToken) {
      return authState.csrfToken;
    }

    const cookieToken = readCookie("XSRF-TOKEN");
    if (cookieToken) {
      authState.csrfToken = cookieToken;
      return cookieToken;
    }

    if (!authState.csrfPromise) {
      authState.csrfPromise = fetch("/csrf", {
        headers: {
          Accept: "application/json"
        }
      })
        .then(async (response) => {
          if (!response.ok) {
            throw new Error("Unable to bootstrap the login session.");
          }
          return response.json();
        })
        .then((payload) => {
          authState.csrfToken = payload.token;
          return payload.token;
        })
        .finally(() => {
          authState.csrfPromise = null;
        });
    }

    return authState.csrfPromise;
  }

  function handleAuthFailure(response) {
    if (response.status !== 401 && response.status !== 403) {
      return false;
    }

    const loginUrl = new URL("/login.html", window.location.origin);
    loginUrl.searchParams.set("reason", "session");
    window.location.assign(loginUrl.toString());
    return true;
  }

  async function readErrorMessage(response) {
    const text = await response.text();
    if (!text) {
      return `Request failed with ${response.status}`;
    }

    try {
      const payload = JSON.parse(text);
      if (payload.message) {
        return payload.message;
      }
      if (payload.error && payload.path) {
        return `${payload.error} (${payload.path})`;
      }
    } catch (error) {
      void error;
    }

    return text;
  }

  function renderLoading(message) {
    return `<div class="loading-state">${escapeHtml(message)}</div>`;
  }

  function renderError(message) {
    return `<div class="error-state">${escapeHtml(message)}</div>`;
  }

  function readCookie(name) {
    const cookies = document.cookie ? document.cookie.split(";") : [];
    for (const cookie of cookies) {
      const [rawName, ...rawValueParts] = cookie.trim().split("=");
      if (rawName === name) {
        return decodeURIComponent(rawValueParts.join("="));
      }
    }
    return null;
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#39;");
  }
})();
