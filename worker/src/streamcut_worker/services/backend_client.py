from __future__ import annotations

import json
from dataclasses import dataclass
from typing import Any
from urllib import error, request

from streamcut_worker.models import ClaimedJob, WorkerExportCompletionPayload, WorkerFailurePayload, WorkerProcessingPayload


class BackendTransportError(RuntimeError):
    pass


@dataclass(slots=True)
class BackendClient:
    base_url: str
    timeout_sec: float = 30.0

    def claim_next_job(self, worker_id: str) -> ClaimedJob | None:
        response = self._post_json(
            "/api/internal/worker/claims/next",
            {"workerId": worker_id},
            allow_no_content=True,
        )
        if response is None:
            return None

        return ClaimedJob(
            job_id=int(response["jobId"]),
            task_type=str(response["taskType"]),
            source_type=str(response["sourceType"]),
            video_path=None if response.get("videoPath") in (None, "") else _path(response["videoPath"]),
            source_url=None if response.get("sourceUrl") in (None, "") else str(response["sourceUrl"]),
            candidate_id=None if response.get("candidateId") is None else int(response["candidateId"]),
            clip_start_sec=None if response.get("clipStartSec") is None else float(response["clipStartSec"]),
            clip_end_sec=None if response.get("clipEndSec") is None else float(response["clipEndSec"]),
            artifact_path=None if response.get("artifactPath") in (None, "") else _path(response["artifactPath"]),
        )

    def submit_result(self, payload: WorkerProcessingPayload) -> dict[str, Any]:
        return self._post_json("/api/internal/worker/results", payload.to_payload())

    def submit_export_result(self, payload: WorkerExportCompletionPayload) -> dict[str, Any]:
        return self._post_json("/api/internal/worker/exports/results", payload.to_payload())

    def submit_failure(self, payload: WorkerFailurePayload) -> dict[str, Any]:
        return self._post_json("/api/internal/worker/failures", payload.to_payload())

    def _post_json(
        self,
        path: str,
        payload: dict[str, Any],
        *,
        allow_no_content: bool = False,
    ) -> dict[str, Any] | None:
        url = f"{self.base_url.rstrip('/')}{path}"
        body = json.dumps(payload).encode("utf-8")
        http_request = request.Request(
            url,
            data=body,
            headers={
                "Content-Type": "application/json",
                "Accept": "application/json",
            },
            method="POST",
        )

        try:
            with request.urlopen(http_request, timeout=self.timeout_sec) as response:
                response_body = response.read()
                if response.status == 204:
                    return None
                if not response_body:
                    return {}
                return json.loads(response_body.decode("utf-8"))
        except error.HTTPError as exc:
            if allow_no_content and exc.code == 204:
                return None
            raise BackendTransportError(
                f"Backend request failed with HTTP {exc.code}: {url}",
            ) from exc
        except error.URLError as exc:
            raise BackendTransportError(
                f"Backend request failed: {url}",
            ) from exc


def _path(value: Any):
    from pathlib import Path

    return Path(str(value))
