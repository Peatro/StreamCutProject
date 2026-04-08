(function () {
  const api = {
    listJobs: () => fetchJson("/api/jobs"),
    getJob: (id) => fetchJson(`/api/jobs/${id}`),
    getTranscript: (id) => fetchJson(`/api/jobs/${id}/transcript`),
    getEvents: (id) => fetchJson(`/api/jobs/${id}/events`),
    getExecutions: (id) => fetchJson(`/api/jobs/${id}/executions`),
    getCandidates: (id) => fetchJson(`/api/jobs/${id}/candidates`),
    retryJob: (id) => postJson(`/api/jobs/${id}/retry`),
    cancelJob: (id) => postJson(`/api/jobs/${id}/cancel`),
    forceFailJob: (id) => postJson(`/api/jobs/${id}/force-fail`),
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

  const reviewState = {
    selectedCandidateId: null,
    root: null,
    keyHandler: null
  };

  let savedEventFilter = "all";

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
    const { job, transcript, events, executions, candidates } = pageData || await loadJobPageData(jobId);

    const queueSummary = summarizeCandidatesForHeader(candidates);
    document.title = `Job #${job.id} - ${labelForJob(job)}`;

    root.innerHTML = `
      <div class="page-status" data-status-banner ${flashMessage ? "" : "hidden"}>
        ${flashMessage ? renderBanner(flashMessage, flashType) : ""}
      </div>
      <section class="page-header">
        <div class="page-header-main">
          <div class="breadcrumbs"><a href="/index.html">Jobs</a> / Job #${escapeHtml(job.id)}</div>
          <div class="page-header-copy">
            <div class="header-actions">
              <span class="live-indicator" data-live-indicator>Live updates booting...</span>
              ${renderStatusPill(job.status)}
            </div>
            <h1>${escapeHtml(labelForJob(job))}</h1>
            <p>Inspect pipeline state, review candidates, and pull finished clips without leaving the job surface.</p>
          </div>
        </div>
        <aside class="page-header-side">
          <div class="header-card">
            <div class="header-card-row">
              <span class="eyebrow">Job Overview</span>
              <button class="action-button action-button-neutral" type="button" data-page-refresh>Refresh</button>
            </div>
            <strong>${escapeHtml(queueSummary)}</strong>
            <span class="muted">Created ${formatRelativeDateTime(job.createdAt)}. Updated ${formatRelativeDateTime(job.updatedAt)}.</span>
          </div>
        </aside>
      </section>
${renderJobFailureSummary(job, candidates)}
      <section class="job-console-grid">
        <div class="job-console-main">
          <section class="panel panel-compact">
            <div class="panel-header">
              <span class="eyebrow">Job Details</span>
            </div>
            <div class="job-meta-grid">
              ${infoItem("Source Type", job.sourceType)}
              ${infoItem("Source URL", job.sourceUrl || "n/a")}
              ${infoItem("Original File", job.originalFilename || "n/a")}
              ${infoItem("Created", formatRelativeDateTime(job.createdAt))}
              ${infoItem("Updated", formatRelativeDateTime(job.updatedAt))}
              ${infoItem("Duration", formatDuration(job.durationSec))}
              ${infoItem("Language", job.language || "n/a")}
              ${infoItem("Started", formatRelativeDateTime(job.startedAt))}
              ${infoItem("Finished", formatRelativeDateTime(job.finishedAt))}
              ${infoItem("Storage Video Path", job.storageVideoPath || "n/a")}
              ${infoItem("Storage Audio Path", job.storageAudioPath || "n/a")}
              ${infoItem("Error", job.errorMessage || "none")}
            </div>
          </section>

          ${renderWorkerRuntimePanel(job)}
          ${renderExecutionHistoryPanel(executions)}

          <!--
          <section class="panel">
            <div class="panel-header">
              <div>
                <h2>Transcript Preview</h2>
                <p>Scan the first recovered segments and their coverage before you drill into clip candidates.</p>
              </div>
            </div>
            ${renderTranscript(transcript)}
          </section>
          -->
        </div>
        <aside class="job-console-side">
          <div class="panel panel-sticky">
            <div class="panel-header">
              <div>
                <h2>Job Events</h2>
                <p>A chronological feed of ingest, analysis, moderation, and export signals.</p>
              </div>
            </div>
            ${renderEvents(events)}
          </div>
        </aside>
      </section>

      <section class="panel">
        <div class="panel-header">
          <div>
            <h2>Candidate Review</h2>
            <p>Clip windows are ready to inspect immediately. Moderate only when you need curation, then download from the same surface.</p>
          </div>
          <div class="shortcut-hint">Shortcuts: A approve | R reject | J/K next/prev</div>
        </div>
        ${renderCandidates(job, candidates)}
      </section>
    `;

    bindCandidateActions(root, jobId);
    bindJobPageActions(root, jobId);
    bindEventControls(root);
    bindCandidatePreviewPlayers(root);
    bindCandidateKeyboardShortcuts(root);
    syncLiveSnapshot("job", buildJobPageSnapshot(job, transcript, events, executions, candidates), root, jobId);
    if (liveUpdates.mode === "job" && String(liveUpdates.jobId) === String(jobId)) {
      updateLiveIndicator(root, "live", describeJobLiveState(job, candidates));
    }
    return { job, transcript, events, executions, candidates };
  }

  async function renderJobsPage(root, flashMessage = null, flashType = "info", jobsData = null) {
    const jobs = jobsData || await api.listJobs();
    const summary = summarizeJobs(jobs);
    document.title = "StreamCut Jobs";

    root.innerHTML = `
      <div class="page-status" data-status-banner ${flashMessage ? "" : "hidden"}>
        ${flashMessage ? renderBanner(flashMessage, flashType) : ""}
      </div>
      <section class="page-header">
        <div class="page-header-main">
          <div class="page-header-copy">
            <div class="header-actions">
              <span class="live-indicator" data-live-indicator>Live updates booting...</span>
            </div>
            <h1>Jobs</h1>
            <p>Queue overview for ingest, processing, moderation, and export. Built to scan fast, not to sell itself.</p>
          </div>
        </div>
        <aside class="page-header-side">
          <div class="header-card">
            <div class="header-card-row">
              <span class="eyebrow">Queue Summary</span>
              <button class="action-button action-button-neutral" type="button" data-jobs-refresh>Refresh List</button>
            </div>
            <strong>${escapeHtml(`${summary.total} job${summary.total !== 1 ? "s" : ""} in queue`)}</strong>
            <span class="muted">${summary.active} active · ${summary.ready} awaiting review · ${summary.finished} finished</span>
          </div>
        </aside>
      </section>
      <section class="stat-grid">
        <article class="stat-card"><span class="label">Jobs</span><span class="value">${summary.total}</span></article>
        <article class="stat-card ${summary.active > 0 ? "is-live" : ""}"><span class="label">Active</span><span class="value">${summary.active}${summary.active > 0 ? '<span class="stat-live-dot" aria-hidden="true"></span>' : ""}</span></article>
        <article class="stat-card"><span class="label">Awaiting Review</span><span class="value">${summary.ready}</span></article>
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
      <section class="panel">
        <div class="panel-header">
          <div>
            <h2>Create Job</h2>
            <p>Submit a VOD URL or upload a local file.</p>
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
              Single file only. Supported formats: MP4, MOV, MKV, WEBM, AVI, MPEG/MPG. Max file size: 512 MB.
            </div>
            <div class="form-message" data-upload-job-message></div>
          </form>
        </div>
      </section>
    `;

    bindJobsPageActions(root);
    syncLiveSnapshot("jobs", buildJobsSnapshot(jobs), root);
    if (liveUpdates.mode === "jobs") {
      updateLiveIndicator(root, "live", describeJobsLiveState(jobs));
    }
    return jobs;
  }

  function initUploadDragDrop(form) {
    const fileInput = form.querySelector("[name='file']");
    const hint = form.querySelector(".upload-hint");
    if (!fileInput) {
      return;
    }

    let dragDepth = 0;

    form.addEventListener("dragenter", (e) => {
      e.preventDefault();
      dragDepth++;
      form.dataset.dragOver = "";
    });

    form.addEventListener("dragleave", () => {
      dragDepth--;
      if (dragDepth <= 0) {
        dragDepth = 0;
        delete form.dataset.dragOver;
      }
    });

    form.addEventListener("dragover", (e) => e.preventDefault());

    form.addEventListener("drop", (e) => {
      e.preventDefault();
      dragDepth = 0;
      delete form.dataset.dragOver;
      const file = e.dataTransfer?.files?.[0];
      if (!file) {
        return;
      }
      try {
        const dt = new DataTransfer();
        dt.items.add(file);
        fileInput.files = dt.files;
        if (hint) {
          hint.textContent = `${file.name} ready to upload.`;
        }
      } catch {
        // DataTransfer assignment not supported — skip
      }
    });
  }

  function bindJobsPageActions(root) {
    const refreshButton = root.querySelector("[data-jobs-refresh]");
    const urlForm = root.querySelector("[data-url-job-form]");
    const uploadForm = root.querySelector("[data-upload-job-form]");

    if (uploadForm) {
      initUploadDragDrop(uploadForm);
    }

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
        const actionLabelByType = {
          retry: "Retrying...",
          cancel: "Canceling...",
          "force-fail": "Force Failing..."
        };
        const previousLabel = button.textContent;
        setLiveInteractionLock(true, `${previousLabel} in progress.`);
        button.disabled = true;
        button.textContent = actionLabelByType[action] || "Working...";

        try {
          if (action === "retry") {
            await api.retryJob(jobId);
            await renderJobPage(root, jobId, `Job #${jobId} was requeued for download.`, "success");
            return;
          }
          if (action === "cancel") {
            await api.cancelJob(jobId);
            await renderJobPage(root, jobId, `Job #${jobId} was canceled.`, "warning");
            return;
          }
          if (action === "force-fail") {
            await api.forceFailJob(jobId);
            await renderJobPage(root, jobId, `Job #${jobId} was force-failed by an operator.`, "warning");
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
    const [job, transcript, events, executions, candidates] = await Promise.all([
      api.getJob(jobId),
      api.getTranscript(jobId),
      api.getEvents(jobId),
      api.getExecutions(jobId),
      api.getCandidates(jobId)
    ]);

    return { job, transcript, events, executions, candidates };
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
    liveUpdates.snapshot = buildJobPageSnapshot(pageData.job, pageData.transcript, pageData.events, pageData.executions, pageData.candidates);
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
      const snapshot = buildJobPageSnapshot(pageData.job, pageData.transcript, pageData.events, pageData.executions, pageData.candidates);
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

  function buildJobPageSnapshot(job, transcript, events, executions, candidates) {
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
      executions: (executions || []).map((execution) => [
        execution.id,
        execution.taskType,
        execution.status,
        execution.workerId,
        execution.processingVersion,
        execution.candidateId,
        execution.claimedAt,
        execution.lastHeartbeatAt,
        execution.finishedAt,
        execution.failureMessage
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
    root.querySelectorAll("[data-live-indicator]").forEach((indicator) => {
      indicator.dataset.state = state;
      indicator.textContent = message;
    });
  }

  function formatLiveClock() {
    return new Intl.DateTimeFormat(undefined, {
      timeStyle: "short"
    }).format(new Date());
  }

  function renderWorkerRuntimePanel(job) {
    const progressPercent = normalizedProgressPercent(job);
    const currentStatus = String(job?.status || "").toUpperCase();
    const canRetry = isJobRetryable(job);
    const canCancel = isJobCancelable(job);
    const canForceFail = isJobForceFailable(job);
    const progressLabel = job?.progressMessage || defaultProgressMessage(job);
    const phaseLabel = formatEventType(currentStatus);
    const heartbeat = describeWorkerHeartbeat(job);
    const stageProgress = describeStageProgress(job);
    const workerLabel = job?.currentWorkerId || "Awaiting worker claim";

    const runtimeBody = `
      <div class="worker-runtime-body">
        <div class="header-actions">
          ${canRetry ? '<button class="action-button action-button-primary" type="button" data-job-control="retry">Retry</button>' : ""}
          ${canCancel ? '<button class="action-button action-button-reject" type="button" data-job-control="cancel">Cancel</button>' : ""}
          ${canForceFail ? '<button class="action-button action-button-reject" type="button" data-job-control="force-fail">Force Fail</button>' : ""}
        </div>
        <div class="worker-progress-shell ${activeWorkerStatuses.has(currentStatus) ? "is-active" : ""}">
          <div class="worker-progress-meta">
            <span class="worker-progress-pill">${escapeHtml(`${progressPercent}%`)}</span>
            <span class="worker-progress-copy">${escapeHtml(progressLabel)}</span>
          </div>
          <div class="worker-progress-detail-row">
            <span class="worker-progress-detail">${escapeHtml(stageProgress.label)}</span>
            <span class="worker-heartbeat-badge worker-heartbeat-${escapeHtml(heartbeat.state)}">${escapeHtml(heartbeat.badge)}</span>
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
              <strong class="micro-card-value micro-card-value-compact">${escapeHtml(heartbeat.label)}</strong>
            </article>
            <article class="micro-card">
              <span class="micro-card-label">Execution</span>
              <strong class="micro-card-value micro-card-value-compact">v${escapeHtml(job?.processingVersion ?? "n/a")}</strong>
            </article>
          </div>
          <div class="footer-note">${escapeHtml(workerActionHint(job))}</div>
        </div>
      </div>
    `;

    if (terminalJobStatuses.has(currentStatus)) {
      return `
        <details class="panel worker-runtime-panel worker-runtime-disclosure">
          <summary class="worker-runtime-summary">
            <div class="worker-runtime-summary-copy">
              <span class="worker-runtime-summary-title">Worker Runtime ${renderStatusPill(job.status)}</span>
              <span class="worker-runtime-summary-meta">${escapeHtml(progressLabel)}</span>
            </div>
            <span class="worker-runtime-summary-toggle" aria-hidden="true"></span>
          </summary>
          ${runtimeBody}
        </details>
      `;
    }

    return `
      <section class="panel worker-runtime-panel">
        <div class="panel-header">
          <div>
            <span class="eyebrow">Worker Runtime</span>
            <h2 class="panel-eyebrow-h2">${escapeHtml(phaseLabel)}</h2>
            <p>${escapeHtml(progressLabel)}</p>
          </div>
        </div>
        ${runtimeBody}
      </section>
    `;
  }

  function renderExecutionHistoryPanel(executions) {
    if (!executions || !executions.length) {
      return `
        <section class="panel execution-history-panel">
          <div class="panel-header">
            <div>
              <span class="eyebrow">Execution History</span>
              <h2 class="panel-eyebrow-h2">No executions yet</h2>
              <p>The worker has not claimed this job yet.</p>
            </div>
          </div>
          <div class="empty-state">Execution claims, retries, recoveries, and completions will appear here.</div>
        </section>
      `;
    }

    const latestExecution = executions[executions.length - 1];
    const latestLabel = latestExecution?.status === "RUNNING"
      ? "Latest execution is active."
      : `Latest execution ${formatEventType(latestExecution?.status || "UNKNOWN").toLowerCase()}.`;

    return `
      <section class="panel execution-history-panel">
        <div class="panel-header">
          <div>
            <span class="eyebrow">Execution History</span>
            <h2 class="panel-eyebrow-h2">${escapeHtml(`${executions.length} execution${executions.length === 1 ? "" : "s"}`)}</h2>
            <p>${escapeHtml(latestLabel)}</p>
          </div>
        </div>
        <div class="execution-history-stack">
          ${executions.slice().reverse().map((execution, index) => renderExecutionHistoryItem(execution, index === 0)).join("")}
        </div>
      </section>
    `;
  }

  function renderExecutionHistoryItem(execution, isLatest) {
    const heartbeatLabel = execution.lastHeartbeatAt
      ? `${formatRelativeDateTime(execution.lastHeartbeatAt)} (${formatElapsedBetween(execution.claimedAt, execution.lastHeartbeatAt)})`
      : "No heartbeat";
    const finishedLabel = execution.finishedAt ? formatRelativeDateTime(execution.finishedAt) : "Still open";
    const candidateLabel = execution.candidateId ? `Candidate #${execution.candidateId}` : "Job-wide execution";
    const failureMessage = execution.failureMessage || "No failure recorded";

    return `
      <article class="execution-history-item ${isLatest ? "is-latest" : ""}">
        <div class="execution-history-top">
          <div class="execution-history-main">
            <span class="pill ${statusClass(execution.status)}">${escapeHtml(execution.taskType)}</span>
            <strong class="execution-history-title">Execution #${escapeHtml(execution.id)}</strong>
            ${isLatest ? '<span class="execution-history-latest">Latest</span>' : ""}
          </div>
          ${renderStatusPill(execution.status)}
        </div>
        <div class="execution-history-grid">
          ${infoItem("Worker", execution.workerId || "n/a")}
          ${infoItem("Role", execution.workerRole || "n/a")}
          ${infoItem("Version", execution.processingVersion ? `v${execution.processingVersion}` : "n/a")}
          ${infoItem("Scope", candidateLabel)}
          ${infoItem("Claimed", formatRelativeDateTime(execution.claimedAt))}
          ${infoItem("Last Heartbeat", heartbeatLabel)}
          ${infoItem("Finished", finishedLabel)}
          ${infoItem("Failure", failureMessage)}
        </div>
      </article>
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
    return formatRelativeDateTime(value);
  }

  function describeWorkerHeartbeat(job) {
    const status = String(job?.status || "").toUpperCase();
    if (!job?.lastWorkerHeartbeatAt) {
      return {
        state: activeWorkerStatuses.has(status) ? "missing" : "idle",
        badge: activeWorkerStatuses.has(status) ? "No heartbeat" : "Idle",
        label: "No heartbeat recorded"
      };
    }

    const heartbeatTime = new Date(job.lastWorkerHeartbeatAt);
    if (Number.isNaN(heartbeatTime.getTime())) {
      return {
        state: "missing",
        badge: "Unknown",
        label: String(job.lastWorkerHeartbeatAt)
      };
    }

    const ageSec = Math.max(0, Math.floor((Date.now() - heartbeatTime.getTime()) / 1000));
    const active = activeWorkerStatuses.has(status);
    const staleThresholdSec = status === "TRANSCRIBING" ? 90 : 60;
    const stalledThresholdSec = status === "TRANSCRIBING" ? 240 : 150;

    if (!active) {
      return {
        state: "idle",
        badge: "Idle",
        label: `${formatRelativeDateTime(job.lastWorkerHeartbeatAt)} (${formatElapsedSeconds(ageSec)} ago)`
      };
    }

    if (ageSec >= stalledThresholdSec) {
      return {
        state: "stalled",
        badge: "Possibly stalled",
        label: `${formatRelativeDateTime(job.lastWorkerHeartbeatAt)} (${formatElapsedSeconds(ageSec)} ago)`
      };
    }

    if (ageSec >= staleThresholdSec) {
      return {
        state: "stale",
        badge: "Heartbeat late",
        label: `${formatRelativeDateTime(job.lastWorkerHeartbeatAt)} (${formatElapsedSeconds(ageSec)} ago)`
      };
    }

    return {
      state: "fresh",
      badge: "Heartbeat fresh",
      label: `${formatRelativeDateTime(job.lastWorkerHeartbeatAt)} (${formatElapsedSeconds(ageSec)} ago)`
    };
  }

  function describeStageProgress(job) {
    const status = String(job?.status || "").toUpperCase();
    const overallPercent = normalizedProgressPercent(job);
    const stages = {
      QUEUED_FOR_DOWNLOAD: { label: "Queued for download", range: [0, 5] },
      DOWNLOADING: { label: "Download", range: [5, 18] },
      QUEUED_FOR_PROCESSING: { label: "Queued for processing", range: [18, 28] },
      EXTRACTING_AUDIO: { label: "Audio extraction", range: [28, 36] },
      TRANSCRIBING: { label: "Transcription", range: [48, 67] },
      DETECTING_SILENCE: { label: "Silence detection", range: [68, 84] },
      ANALYZING_WINDOWS: { label: "Window analysis", range: [84, 94] },
      GENERATING_CANDIDATES: { label: "Candidate generation", range: [94, 100] },
      EXPORTING_CLIP: { label: "Clip export", range: [92, 100] }
    };

    const stage = stages[status];
    if (!stage) {
      return {
        label: "Overall pipeline",
        detail: `Overall pipeline ${overallPercent}%`
      };
    }

    const [start, end] = stage.range;
    const span = Math.max(1, end - start);
    const stagePercent = Math.max(0, Math.min(Math.round(((overallPercent - start) / span) * 100), 100));

    return {
      label: `${stage.label} in progress`,
      detail: `${stage.label} ${stagePercent}% of stage`
    };
  }

  function formatElapsedSeconds(seconds) {
    if (seconds < 60) {
      return `${seconds}s`;
    }
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    if (minutes < 60) {
      return remainingSeconds > 0 ? `${minutes}m ${remainingSeconds}s` : `${minutes}m`;
    }
    const hours = Math.floor(minutes / 60);
    const remainingMinutes = minutes % 60;
    return remainingMinutes > 0 ? `${hours}h ${remainingMinutes}m` : `${hours}h`;
  }

  function isJobCancelable(job) {
    const status = String(job?.status || "").toUpperCase();
    return ["QUEUED_FOR_DOWNLOAD", "QUEUED_FOR_PROCESSING"].includes(status);
  }

  function isJobRetryable(job) {
    const status = String(job?.status || "").toUpperCase();
    return status === "FAILED";
  }

  function isJobForceFailable(job) {
    const status = String(job?.status || "").toUpperCase();
    return ["DOWNLOADING", "EXTRACTING_AUDIO", "TRANSCRIBING", "DETECTING_SILENCE", "ANALYZING_WINDOWS", "GENERATING_CANDIDATES", "EXPORTING_CLIP"].includes(status);
  }

  function workerActionHint(job) {
    const status = String(job?.status || "").toUpperCase();
    if (isJobRetryable(job)) {
      return "Retry requeues a failed job from the download stage and increments the processing version.";
    }
    if (isJobCancelable(job)) {
      return "Cancel permanently stops a queued job before a worker picks it up.";
    }
    if (isJobForceFailable(job)) {
      return "Force Fail marks the active worker run as failed and records an explicit operator recovery event.";
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
          <details class="candidate-card" data-candidate-card data-candidate-id="${escapeHtml(candidate.id)}" ${shouldExpandCandidate(candidate) ? "open" : ""}>
            <summary class="candidate-summary">
              <div class="candidate-summary-main">
                <span class="candidate-summary-text">${timeRange(candidate.startSec, candidate.endSec)}</span>
                ${renderScoreBar(candidate.score)}
                ${renderStatusPill(candidate.moderationStatus)}
              </div>
              <span class="candidate-summary-toggle" aria-hidden="true">▾</span>
            </summary>
            <div class="candidate-body">
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
                <strong>${timeRange(candidate.startSec, candidate.endSec)}</strong>
                ${renderStatusPill(candidate.moderationStatus)}
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
                <button class="action-button action-button-approve" type="button" title="Approve candidate (A)" data-shortcut="A" data-candidate-action="approve" data-candidate-id="${escapeHtml(candidate.id)}" ${candidate.moderationStatus === "APPROVED" ? "disabled" : ""}>Approve</button>
                <button class="action-button action-button-reject" type="button" title="Reject candidate (R)" data-shortcut="R" data-candidate-action="reject" data-candidate-id="${escapeHtml(candidate.id)}" ${candidate.moderationStatus === "REJECTED" ? "disabled" : ""}>Reject</button>
                <button class="action-button action-button-export" type="button" title="${candidate.exportReady ? "Download the prepared clip" : "Prepare and download the clip"}" data-candidate-action="download" data-candidate-id="${escapeHtml(candidate.id)}" data-export-ready="${candidate.exportReady}" ${candidate.exportStatus === "IN_PROGRESS" ? "disabled" : ""}>${candidate.exportReady ? "Download" : "Export & Download"}</button>
              </div>
              <div class="candidate-message" data-candidate-message></div>
            </div>
          </details>
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

  function shouldExpandCandidate(candidate) {
    return !["APPROVED", "REJECTED"].includes(String(candidate?.moderationStatus || "").toUpperCase());
  }

  function bindCandidateKeyboardShortcuts(root) {
    reviewState.root = root;
    const cards = Array.from(root.querySelectorAll("[data-candidate-card]"));
    if (!cards.length) {
      return;
    }

    cards.forEach((card) => {
      const summary = card.querySelector(".candidate-summary");
      summary?.addEventListener("click", () => {
        setSelectedCandidate(card.dataset.candidateId);
      });
      card.addEventListener("toggle", () => {
        if (card.open) {
          setSelectedCandidate(card.dataset.candidateId);
        }
      });
    });

    const selectedId = cards.some((card) => card.dataset.candidateId === reviewState.selectedCandidateId)
      ? reviewState.selectedCandidateId
      : cards.find((card) => card.open)?.dataset.candidateId || cards[0].dataset.candidateId;

    setSelectedCandidate(selectedId);

    if (!reviewState.keyHandler) {
      reviewState.keyHandler = (event) => {
        if (document.body.dataset.page !== "job") {
          return;
        }
        if (event.metaKey || event.ctrlKey || event.altKey) {
          return;
        }
        const target = event.target;
        const tagName = target?.tagName;
        if (target?.isContentEditable || ["INPUT", "TEXTAREA", "SELECT"].includes(tagName)) {
          return;
        }

        if (event.key === "j" || event.key === "k") {
          event.preventDefault();
          moveSelectedCandidate(event.key === "j" ? 1 : -1);
          return;
        }

        if (event.key === "a" || event.key === "r") {
          const activeCard = getSelectedCandidateCard();
          if (!activeCard) {
            return;
          }
          const action = event.key === "a" ? "approve" : "reject";
          const button = activeCard.querySelector(`[data-candidate-action="${action}"]:not(:disabled)`);
          if (button) {
            event.preventDefault();
            button.click();
          }
        }
      };
      document.addEventListener("keydown", reviewState.keyHandler);
    }
  }

  function getCandidateCards() {
    return Array.from(reviewState.root?.querySelectorAll("[data-candidate-card]") || []);
  }

  function getSelectedCandidateCard() {
    return getCandidateCards().find((card) => card.dataset.candidateId === reviewState.selectedCandidateId) || null;
  }

  function setSelectedCandidate(candidateId) {
    reviewState.selectedCandidateId = candidateId;
    getCandidateCards().forEach((card) => {
      const isSelected = card.dataset.candidateId === candidateId;
      card.classList.toggle("is-selected", isSelected);
    });
  }

  function moveSelectedCandidate(direction) {
    const cards = getCandidateCards();
    if (!cards.length) {
      return;
    }

    const currentIndex = Math.max(0, cards.findIndex((card) => card.dataset.candidateId === reviewState.selectedCandidateId));
    const nextIndex = Math.max(0, Math.min(cards.length - 1, currentIndex + direction));
    const nextCard = cards[nextIndex];
    if (!nextCard) {
      return;
    }

    nextCard.open = true;
    setSelectedCandidate(nextCard.dataset.candidateId);
    nextCard.scrollIntoView({ block: "nearest", behavior: "smooth" });
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
        <td>${renderStatusPill(job.status)}</td>
        <td>${renderCompactProgress(job)}</td>
        <td>${escapeHtml(sourceLabel(job))}</td>
        <td title="${escapeHtml(formatDate(job.createdAt))}">${escapeHtml(formatRelativeDateTime(job.createdAt))}</td>
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
              <th>Progress</th>
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
    const stageProgress = describeStageProgress(job);
    const heartbeat = describeWorkerHeartbeat(job);
    const latestExecution = job?.latestExecution || null;
    const executionSummary = latestExecution ? summarizeLatestExecution(latestExecution) : "No execution yet";
    return `
      <div class="table-progress">
        <div class="table-progress-copy">${escapeHtml(progressLabel)}</div>
        <div class="table-progress-track" aria-hidden="true">
          <span class="table-progress-fill" style="width: ${escapeHtml(progressPercent)}%;"></span>
        </div>
        <div class="table-progress-meta">${escapeHtml(`${progressPercent}% | ${stageProgress.label}`)}</div>
        <div class="table-progress-meta table-progress-meta-secondary">${escapeHtml(heartbeat.badge)}${job?.currentWorkerId ? ` | ${escapeHtml(job.currentWorkerId)}` : ""}</div>
        <div class="table-progress-meta table-progress-meta-secondary">${escapeHtml(executionSummary)}</div>
      </div>
    `;
  }

  function summarizeLatestExecution(execution) {
    const task = formatEventType(execution.taskType || "UNKNOWN");
    const status = formatEventType(execution.status || "UNKNOWN");
    if (execution.failureMessage) {
      return `${task} | ${status} | ${execution.failureMessage}`;
    }
    if (execution.workerId) {
      return `${task} | ${status} | ${execution.workerId}`;
    }
    return `${task} | ${status}`;
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
    const importantEvents = events.filter((event, index) => isImportantEvent(event, index, events));
    const visibleImportantEvents = importantEvents.slice(-7);
    const hiddenImportantCount = Math.max(importantEvents.length - visibleImportantEvents.length, 0);
    const rawPreviewEvents = events.slice(-6).reverse();
    const hiddenRawCount = Math.max(events.length - rawPreviewEvents.length, 0);
    const phaseSummary = summarizeEventPhases(events);
    const totalElapsed = formatElapsedBetween(events[0]?.createdAt, latestEvent?.createdAt);

    return `
      <div class="event-overview">
        <article class="event-hero-card">
          <span class="micro-card-label">Current Phase</span>
          <strong class="event-hero-title">${escapeHtml(formatEventType(latestEvent.eventType))}</strong>
          <div class="event-hero-meta">
            <span>${escapeHtml(formatRelativeDateTime(latestEvent.createdAt))}</span>
            <span>${escapeHtml(totalElapsed)} total elapsed</span>
          </div>
          <p class="event-hero-copy">${escapeHtml(latestEvent.message || "No event message recorded.")}</p>
        </article>
        <div class="event-phase-grid">
          ${phaseSummary.map((phase) => `
            <article class="event-phase-card ${phase.tone}">
              <div class="event-phase-top">
                <span class="event-phase-label">${escapeHtml(phase.label)}</span>
                <span class="event-phase-icon" aria-hidden="true">${escapeHtml(phase.icon)}</span>
              </div>
              <strong class="event-phase-title">${escapeHtml(phase.title)}</strong>
              <div class="event-phase-meta">
                <span>${escapeHtml(formatRelativeDateTime(phase.createdAt))}</span>
                <span>${escapeHtml(phase.elapsed)}</span>
              </div>
            </article>
          `).join("")}
        </div>
      </div>
      <div class="event-panel">
        <div class="event-panel-head">
          <div>
            <h3>Operator Timeline</h3>
            <p>Important transitions only by default. Raw worker log stays available below.</p>
          </div>
          <div class="event-filter-bar" data-event-filters>
            ${renderEventFilterButton("all", "All", savedEventFilter === "all")}
            ${renderEventFilterButton("important", "Important", savedEventFilter === "important")}
            ${renderEventFilterButton("worker", "Worker", savedEventFilter === "worker")}
            ${renderEventFilterButton("moderation", "Moderation", savedEventFilter === "moderation")}
            ${renderEventFilterButton("errors", "Errors", savedEventFilter === "errors")}
          </div>
        </div>
        ${hiddenImportantCount > 0 ? `<div class="footer-note">Showing the last ${visibleImportantEvents.length} important events. ${hiddenImportantCount} earlier transitions are folded into the raw log.</div>` : ""}
        <div class="event-feed event-feed-compact" data-event-feed>
          ${visibleImportantEvents.map((event, index) => renderImportantEventRow(event, index, visibleImportantEvents)).join("")}
        </div>
      </div>
      <details class="raw-events-panel">
        <summary class="raw-events-summary">
          <div class="raw-events-summary-copy">
            <span class="raw-events-title">Raw Event Log</span>
            <span class="raw-events-meta">${hiddenRawCount > 0 ? `${hiddenRawCount} earlier events hidden` : "Showing latest worker log entries"}</span>
          </div>
          <span class="raw-events-toggle" aria-hidden="true"></span>
        </summary>
        <div class="raw-events-list">
          ${rawPreviewEvents.map((event) => renderRawEventItem(event)).join("")}
          ${hiddenRawCount > 0 ? `<div class="footer-note">Latest ${rawPreviewEvents.length} raw events shown. Full event history remains available from the backend API.</div>` : ""}
        </div>
      </details>
    `;
  }

  function bindEventControls(root) {
    const container = root.querySelector("[data-event-filters]");
    const feed = root.querySelector("[data-event-feed]");
    if (!container || !feed) {
      return;
    }

    container.querySelectorAll("[data-event-filter-control]").forEach((button) => {
      button.addEventListener("click", () => {
        const filter = button.dataset.eventFilterControl || "all";
        savedEventFilter = filter;
        container.querySelectorAll("[data-event-filter-control]").forEach((control) => {
          control.dataset.active = String(control === button);
        });
        feed.querySelectorAll("[data-event-item]").forEach((item) => {
          const categories = (item.dataset.eventCategories || "").split(" ");
          const visible = filter === "all" || categories.includes(filter);
          item.hidden = !visible;
        });
      });
    });

    if (savedEventFilter !== "all") {
      feed.querySelectorAll("[data-event-item]").forEach((item) => {
        const categories = (item.dataset.eventCategories || "").split(" ");
        item.hidden = !categories.includes(savedEventFilter);
      });
    }
  }

  function renderEventFilterButton(value, label, active = false) {
    return `<button class="event-filter-button" type="button" data-event-filter-control="${escapeHtml(value)}" data-active="${active ? "true" : "false"}">${escapeHtml(label)}</button>`;
  }

  function renderImportantEventRow(event, index, events) {
    const categories = eventCategories(event).join(" ");
    const previousEvent = index > 0 ? events[index - 1] : null;
    return `
      <article class="event-row event-row-compact ${eventToneClass(event.eventType)}" data-event-item data-event-categories="${escapeHtml(categories)}">
        <div class="event-rail" aria-hidden="true">
          <span class="event-node ${eventToneClass(event.eventType)}"></span>
          ${index === events.length - 1 ? "" : '<span class="event-rail-line"></span>'}
        </div>
        <div class="event-card ${eventToneClass(event.eventType)}">
          <div class="event-meta">
            <span class="event-type-pill ${eventToneClass(event.eventType)}">${escapeHtml(`${eventIcon(event.eventType)} ${formatEventType(event.eventType)}`)}</span>
            <span class="event-time">${escapeHtml(formatRelativeDateTime(event.createdAt))}</span>
          </div>
          <p class="event-message">${escapeHtml(event.message || "No event message recorded.")}</p>
          <div class="event-row-foot">
            <span>${escapeHtml(formatEventPhaseLabel(event.eventType))}</span>
            <span>${escapeHtml(formatElapsedBetween(previousEvent?.createdAt, event.createdAt))}</span>
          </div>
        </div>
      </article>
    `;
  }

  function renderRawEventItem(event) {
    return `
      <details class="raw-event-item">
        <summary class="raw-event-summary">
          <div class="raw-event-main">
            <span class="raw-event-icon" aria-hidden="true">${escapeHtml(eventIcon(event.eventType))}</span>
            <span class="raw-event-type">${escapeHtml(formatEventType(event.eventType))}</span>
          </div>
          <span class="raw-event-time">${escapeHtml(formatRelativeDateTime(event.createdAt))}</span>
        </summary>
        <div class="raw-event-body">
          <div class="raw-event-body-row"><span>Phase</span><strong>${escapeHtml(formatEventPhaseLabel(event.eventType))}</strong></div>
          <div class="raw-event-body-row"><span>Type</span><strong>${escapeHtml(event.eventType || "n/a")}</strong></div>
          <p class="raw-event-message">${escapeHtml(event.message || "No event message recorded.")}</p>
        </div>
      </details>
    `;
  }

  function summarizeEventPhases(events) {
    const phases = ["ingest", "processing", "moderation", "export", "failure"];
    return phases
      .map((phase) => {
        const phaseEvents = events.filter((event) => eventPhase(event.eventType) === phase);
        if (!phaseEvents.length) {
          return null;
        }
        const firstEvent = phaseEvents[0];
        const latestEvent = phaseEvents[phaseEvents.length - 1];
        return {
          label: phaseLabel(phase),
          title: formatEventType(latestEvent.eventType),
          createdAt: latestEvent.createdAt,
          elapsed: formatElapsedBetween(firstEvent.createdAt, latestEvent.createdAt),
          icon: phaseIcon(phase),
          tone: `tone-${phaseTone(phase)}`
        };
      })
      .filter(Boolean);
  }

  function isImportantEvent(event, index, events) {
    const type = String(event?.eventType || "").toUpperCase();
    const importantTypes = new Set([
      "JOB_CREATED",
      "JOB_QUEUED_FOR_DOWNLOAD",
      "JOB_DOWNLOAD_COMPLETED",
      "JOB_QUEUED_FOR_PROCESSING",
      "JOB_READY_FOR_REVIEW",
      "EXPORT_STARTED",
      "EXPORT_COMPLETED",
      "JOB_RETRIED",
      "JOB_FAILED",
      "JOB_CANCELED",
      "JOB_FORCE_FAILED"
    ]);
    if (importantTypes.has(type)) {
      return true;
    }
    return index === 0 || index === events.length - 1;
  }

  function eventCategories(event) {
    const phase = eventPhase(event.eventType);
    const categories = ["all", "important"];
    if (["ingest", "processing", "export"].includes(phase)) {
      categories.push("worker");
    }
    if (phase === "moderation") {
      categories.push("moderation");
    }
    if (phase === "failure") {
      categories.push("worker", "errors");
    }
    return Array.from(new Set(categories));
  }

  function formatEventPhaseLabel(eventType) {
    return phaseLabel(eventPhase(eventType));
  }

  function phaseLabel(phase) {
    const labels = {
      ingest: "Ingest",
      processing: "Processing",
      moderation: "Moderation",
      export: "Export",
      failure: "Failure",
      system: "System"
    };
    return labels[phase] || "System";
  }

  function phaseIcon(phase) {
    const icons = {
      ingest: "↓",
      processing: "⚙",
      moderation: "✎",
      export: "⬇",
      failure: "✕",
      system: "•"
    };
    return icons[phase] || "•";
  }

  function phaseTone(phase) {
    if (phase === "failure") {
      return "danger";
    }
    if (phase === "moderation" || phase === "export") {
      return "accent";
    }
    return "success";
  }

  function eventPhase(eventType) {
    const value = String(eventType || "").toUpperCase();
    if (value.includes("FAILED") || value.includes("CANCELED")) {
      return "failure";
    }
    if (value.includes("EXPORT")) {
      return "export";
    }
    if (value.includes("REVIEW")) {
      return "moderation";
    }
    if (value.includes("DOWNLOAD") || value.includes("CREATED")) {
      return "ingest";
    }
    if (value.includes("PROCESSING") || value.includes("CLAIMED") || value.includes("RETRIED")) {
      return "processing";
    }
    return "system";
  }

  function eventIcon(eventType) {
    return phaseIcon(eventPhase(eventType));
  }

  function formatElapsedBetween(startValue, endValue) {
    if (!startValue || !endValue) {
      return "n/a";
    }
    const start = new Date(startValue);
    const end = new Date(endValue);
    if (Number.isNaN(start.getTime()) || Number.isNaN(end.getTime())) {
      return "n/a";
    }
    const diffSeconds = Math.max(0, Math.floor((end.getTime() - start.getTime()) / 1000));
    if (diffSeconds < 60) {
      return `${diffSeconds}s`;
    }
    const hours = Math.floor(diffSeconds / 3600);
    const minutes = Math.floor((diffSeconds % 3600) / 60);
    const seconds = diffSeconds % 60;
    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    }
    if (minutes > 0 && seconds > 0) {
      return `${minutes}m ${seconds}s`;
    }
    return `${minutes}m`;
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

  function summarizeCandidatesForHeader(candidates) {
    const rejected = candidates.filter((candidate) => candidate.moderationStatus === "REJECTED").length;
    const summary = summarizeCandidates(candidates);
    return `${summary.pending} pending | ${summary.approved} approved | ${rejected} rejected`;
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

  function renderStatusPill(status) {
    const label = String(status || "UNKNOWN");
    return `
      <span class="pill ${statusClass(label)}">
        <span class="pill-icon" aria-hidden="true">${escapeHtml(statusIcon(label))}</span>
        <span>${escapeHtml(label)}</span>
      </span>
    `;
  }

  function statusIcon(status) {
    const value = String(status || "").toUpperCase();
    if (["COMPLETED", "APPROVED"].includes(value)) {
      return "✓";
    }
    if (["FAILED", "REJECTED"].includes(value)) {
      return "✕";
    }
    if (["PENDING"].includes(value)) {
      return "◌";
    }
    if (activeWorkerStatuses.has(value) || ["READY_FOR_REVIEW", "EXPORTING_CLIP", "IN_PROGRESS"].includes(value)) {
      return "●";
    }
    return "◌";
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

  function renderScoreBar(score) {
    const numericScore = Number(score);
    const normalized = Number.isNaN(numericScore)
      ? 0
      : Math.max(0, Math.min(Math.round(numericScore * 100), 100));

    return `
      <span class="score-bar">
        <span class="score-track" aria-hidden="true">
          <span class="score-fill" style="width: ${normalized}%;"></span>
        </span>
        <span class="score-value">${escapeHtml(formatScore(score))}</span>
      </span>
    `;
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

  function formatShortDate(value) {
    if (!value) {
      return "n/a";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return String(value);
    }
    return new Intl.DateTimeFormat(undefined, {
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
      hour12: false
    }).format(date);
  }

  function formatRelativeDate(value) {
    if (!value) {
      return "n/a";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return String(value);
    }

    const diffMs = Date.now() - date.getTime();
    if (diffMs < 0) {
      return formatShortDate(value);
    }

    const diffMinutes = Math.floor(diffMs / 60000);
    if (diffMinutes < 60) {
      if (diffMinutes <= 0) {
        return "just now";
      }
      return `${diffMinutes} min ago`;
    }

    const diffHours = Math.floor(diffMinutes / 60);
    if (diffHours < 24) {
      return `${diffHours} hour${diffHours === 1 ? "" : "s"} ago`;
    }

    return formatShortDate(value);
  }

  function formatRelativeDateTime(value) {
    return formatRelativeDate(value);
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
