from __future__ import annotations

import subprocess
from dataclasses import dataclass
from typing import Protocol, Sequence

from .models import ProcessExecutionResult


class ProcessRunner(Protocol):
    def run(self, command: Sequence[str]) -> ProcessExecutionResult:
        raise NotImplementedError


@dataclass(slots=True)
class SubprocessProcessRunner:
    def run(self, command: Sequence[str]) -> ProcessExecutionResult:
        completed = subprocess.run(
            list(command),
            capture_output=True,
            text=True,
            check=False,
        )
        return ProcessExecutionResult(
            returncode=completed.returncode,
            stdout=completed.stdout,
            stderr=completed.stderr,
        )

