# TASK-032 Implement Worker Polling Loop

## Agent
worker-agent

## Summary
Implement a real long-running worker loop that polls backend for queued jobs and hands claimed payloads to the processing runner.

## Context
The current worker stays alive but does not actually consume jobs. The service cannot function end-to-end until the worker repeatedly claims and processes real work.

## Scope
- add backend polling client to worker
- claim work from backend on a loop
- hand claimed payloads to a processing runner boundary
- report success and failure back to backend using the documented transport

## Out of Scope
- detailed media-processing implementation changes
- broker integration
- advanced retry policy
- orchestration inside the backend

## Inputs
- worker-protocol.md
- TASK-026.md
- TASK-029.md
- TASK-030.md
- TASK-031.md

## Expected Deliverables
- code
- config
- docs if startup/env changes are needed

## Constraints
- keep the loop simple and observable
- do not write directly to backend database
- worker must speak only through the transport contract

## Acceptance Criteria
- worker can poll backend for queued jobs
- claimed jobs are passed into worker processing flow
- worker sends success or failure payloads back to backend
- local runtime can leave the worker running without manual intervention

## Notes
The worker loop should be interruptible and suitable for Docker execution.
