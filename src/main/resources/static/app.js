(function () {
  const api = {
    listJobs: () => fetchJson("/api/jobs"),
    getJob: (id) => fetchJson(`/api/jobs/${id}`),
    getTranscript: (id) => fetchJson(`/api/jobs/${id}/transcript`),
    getEvents: (id) => fetchJson(`/api/jobs/${id}/events`),
    getCandidates: (id) => fetchJson(`/api/jobs/${id}/candidates`),
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

  async function initJobsPage() {
    const root = document.querySelector("[data-page-root]");
    root.innerHTML = renderLoading("Loading jobs...");

    await renderJobsPage(root);
  }

  async function initJobPage() {
    const root = document.querySelector("[data-page-root]");
    const jobId = new URLSearchParams(window.location.search).get("id");

    if (!jobId) {
      root.innerHTML = renderError("Missing job id in the URL.");
      return;
    }

    root.innerHTML = renderLoading("Loading job details...");
    await renderJobPage(root, jobId);
  }

  async function renderJobPage(root, jobId, flashMessage = null, flashType = "info") {
    const [job, transcript, events, candidates] = await Promise.all([
      api.getJob(jobId),
      api.getTranscript(jobId),
      api.getEvents(jobId),
      api.getCandidates(jobId)
    ]);

    const candidateSummary = summarizeCandidates(candidates);

    root.innerHTML = `
      <div class="page-status" data-status-banner ${flashMessage ? "" : "hidden"}>
        ${flashMessage ? renderBanner(flashMessage, flashType) : ""}
      </div>
      <div class="breadcrumbs"><a href="/index.html">Jobs</a> / Job #${escapeHtml(job.id)}</div>
      <section class="panel">
        <div class="panel-header">
          <div>
            <span class="eyebrow">Job Details</span>
            <h2 style="margin-top: 12px; font-size: 1.6rem;">${escapeHtml(labelForJob(job))}</h2>
          </div>
          <div class="header-actions">
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
              <p>First transcript segments ordered by start time.</p>
            </div>
          </div>
          ${renderTranscript(transcript)}
        </div>
        <div class="panel">
          <div class="panel-header">
            <div>
              <h2>Job Events</h2>
              <p>Pipeline and moderation history.</p>
            </div>
          </div>
          ${renderEvents(events)}
        </div>
      </section>

      <section class="panel">
        <div class="panel-header">
          <div>
            <h2>Candidate Review</h2>
            <p>Approve, reject, and trigger export from the same surface.</p>
          </div>
        </div>
        ${renderCandidates(candidates)}
      </section>
    `;

    bindCandidateActions(root, jobId);
    bindJobPageActions(root, jobId);
  }

  async function renderJobsPage(root, flashMessage = null, flashType = "info") {
    const jobs = await api.listJobs();
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
          <button class="action-button action-button-neutral" type="button" data-jobs-refresh>Refresh List</button>
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
  }

  function bindJobsPageActions(root) {
    const refreshButton = root.querySelector("[data-jobs-refresh]");
    const urlForm = root.querySelector("[data-url-job-form]");
    const uploadForm = root.querySelector("[data-upload-job-form]");

    refreshButton?.addEventListener("click", async () => {
      refreshButton.disabled = true;
      const previousLabel = refreshButton.textContent;
      refreshButton.textContent = "Refreshing...";
      try {
        await renderJobsPage(root, "Job list refreshed.", "info");
      } catch (error) {
        showInlineBanner(root, error.message || "Unable to refresh jobs.", "error");
        refreshButton.disabled = false;
        refreshButton.textContent = previousLabel;
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
      }
    });
  }

  function bindJobPageActions(root, jobId) {
    const refreshButton = root.querySelector("[data-page-refresh]");
    refreshButton?.addEventListener("click", async () => {
      refreshButton.disabled = true;
      const previousLabel = refreshButton.textContent;
      refreshButton.textContent = "Refreshing...";
      try {
        await renderJobPage(root, jobId, `Job #${jobId} refreshed.`, "info");
      } catch (error) {
        showInlineBanner(root, error.message || "Unable to refresh job.", "error");
        refreshButton.disabled = false;
        refreshButton.textContent = previousLabel;
      }
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
          throw new Error("Unknown action.");
        } catch (error) {
          setCardMessage(message, error.message || "Action failed.");
          event.currentTarget.disabled = false;
          event.currentTarget.textContent = previousLabel;
          showInlineBanner(root, error.message || "Action failed.", "error");
        }
      });
    });
  }

  function renderCandidates(candidates) {
    if (!candidates.length) {
      return `<div class="empty-state">No candidates available yet.</div>`;
    }

    return `
      <div class="stack">
        ${candidates.map((candidate) => `
          <article class="candidate-card" data-candidate-card>
            ${candidate.exportReady ? `
              <div class="candidate-preview-shell">
                <video
                  class="candidate-preview"
                  controls
                  preload="metadata"
                  playsinline
                  src="/api/exports/${encodeURIComponent(candidate.id)}/stream"></video>
              </div>
            ` : ""}
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
                : (candidate.exportedClipPath ? "Export in progress" : "Not exported")}</span>
            </div>
            ${renderCandidateRuntimeState(candidate)}
            <div class="candidate-actions">
              <button class="action-button action-button-approve" type="button" data-candidate-action="approve" data-candidate-id="${escapeHtml(candidate.id)}" ${candidate.moderationStatus === "APPROVED" ? "disabled" : ""}>Approve</button>
              <button class="action-button action-button-reject" type="button" data-candidate-action="reject" data-candidate-id="${escapeHtml(candidate.id)}" ${candidate.moderationStatus === "REJECTED" ? "disabled" : ""}>Reject</button>
              <button class="action-button action-button-export" type="button" data-candidate-action="export" data-candidate-id="${escapeHtml(candidate.id)}" ${candidate.moderationStatus !== "APPROVED" || candidate.exportedClipPath ? "disabled" : ""}>Export</button>
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

  function renderCandidateRuntimeState(candidate) {
    if (candidate.exportReady) {
      return `
        <div class="runtime-state runtime-state-ready">
          <span class="runtime-dot"></span>
          <span>Export artifact is ready.</span>
        </div>
      `;
    }

    if (candidate.exportedClipPath) {
      return `
        <div class="runtime-progress" aria-label="Export in progress">
          <div class="runtime-progress-head">
            <div class="runtime-spinner" aria-hidden="true"></div>
            <span>Worker is exporting this clip</span>
          </div>
          <div class="runtime-progress-track" aria-hidden="true">
            <div class="runtime-progress-bar"></div>
          </div>
        </div>
      `;
    }

    return "";
  }

  function renderJobsTable(jobs) {
    if (!jobs.length) {
      return `<div class="empty-state">No jobs yet. Submit a URL or upload a file to create the first one.</div>`;
    }

    const rows = jobs.map((job) => `
      <tr>
        <td><a href="/job.html?id=${encodeURIComponent(job.id)}">Job #${escapeHtml(job.id)}</a></td>
        <td><span class="pill ${statusClass(job.status)}">${escapeHtml(job.status)}</span></td>
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

  function renderTranscript(transcript) {
    if (!transcript.length) {
      return `<div class="empty-state">Transcript is empty or not ready yet.</div>`;
    }

    return `
      <div class="transcript-preview">
        ${transcript.slice(0, 8).map((segment) => `
          <article class="transcript-line">
            <span class="transcript-time">${timeRange(segment.startSec, segment.endSec)}</span>
            <div>${escapeHtml(segment.text || "")}</div>
          </article>
        `).join("")}
      </div>
      <div class="footer-note">Showing ${Math.min(transcript.length, 8)} of ${transcript.length} segments.</div>
    `;
  }

  function renderEvents(events) {
    if (!events.length) {
      return `<div class="empty-state">No job events recorded yet.</div>`;
    }

    return `
      <div class="stack">
        ${events.map((event) => `
          <article class="event-card">
            <div class="event-top">
              <strong>${escapeHtml(event.eventType)}</strong>
              <span class="muted">${escapeHtml(formatDate(event.createdAt))}</span>
            </div>
            <p class="muted" style="margin: 10px 0 0;">${escapeHtml(event.message || "")}</p>
          </article>
        `).join("")}
      </div>
    `;
  }

  function summarizeJobs(jobs) {
    const activeStatuses = new Set(["NEW", "QUEUED", "DOWNLOADING", "EXTRACTING_AUDIO", "TRANSCRIBING", "DETECTING_SILENCE", "ANALYZING_WINDOWS", "GENERATING_CANDIDATES", "READY_FOR_REVIEW", "EXPORTING_CLIP"]);
    const readyStatuses = new Set(["READY_FOR_REVIEW"]);
    const finishedStatuses = new Set(["COMPLETED", "FAILED"]);
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

  function timeRange(startSec, endSec) {
    if (startSec === null || startSec === undefined || endSec === null || endSec === undefined) {
      return "n/a";
    }
    return `${Number(startSec).toFixed(2)}s - ${Number(endSec).toFixed(2)}s`;
  }

  async function fetchJson(url) {
    const response = await fetch(url, {
      headers: {
        Accept: "application/json"
      }
    });
    if (!response.ok) {
      throw new Error(await readErrorMessage(response));
    }
    return response.json();
  }

  async function postJson(url, options = {}) {
    const response = await fetch(url, {
      method: "POST",
      headers: {
        Accept: "application/json",
        ...(options.headers || {})
      },
      body: options.body
    });
    if (!response.ok) {
      throw new Error(await readErrorMessage(response));
    }
    return response.json();
  }

  async function postMultipart(url, formData) {
    const response = await fetch(url, {
      method: "POST",
      headers: {
        Accept: "application/json"
      },
      body: formData
    });
    if (!response.ok) {
      throw new Error(await readErrorMessage(response));
    }
    return response.json();
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

  function escapeHtml(value) {
    return String(value ?? "")
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#39;");
  }
})();
