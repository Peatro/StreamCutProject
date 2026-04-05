# Git Workflow

## Purpose
This repository uses a lightweight GitFlow model.
The goal is predictable delivery without adding release-process overhead too early.

## Main Branches
- `main` is the stable branch.
- `develop` is the integration branch for completed task work before release.

## Working Branches
- `feature/TASK-XXX-short-name` for normal task implementation
- `fix/TASK-XXX-short-name` for bug fixes tied to an existing task
- `hotfix/short-name` for urgent fixes that must go to `main`
- `release/x.y.z` only when formal release preparation becomes necessary

## Rules
- Do not commit implementation directly to `main`.
- Do not commit implementation directly to `develop` unless explicitly approved.
- One task should map to one working branch where practical.
- Branch names should reference the task id when the work comes from `agents/tasks`.
- Merge completed feature work into `develop` after review.
- Merge `develop` into `main` only for stable milestones.

## Pull Request Intent
- `feature/*` -> `develop`
- `fix/*` -> `develop`
- `hotfix/*` -> `main`, then back-merge into `develop`
- `release/*` -> `main`, then back-merge into `develop`

## Commit Guidance
- Keep commits scoped to one task or one coherent fix.
- Prefer clear commit messages such as:
  - `TASK-011 create job API`
  - `TASK-020 add candidate moderation endpoints`
  - `docs define git workflow`

## MVP Simplification
Until the project reaches its first real release:
- `main` remains the stable baseline
- `develop` is the default integration target
- `release/*` branches are optional
- `hotfix/*` should be rare

## Agent Usage
- Orchestrator assigns one task at a time.
- The implementation agent works in a task branch.
- Reviewer checks scope before merge.
- QA validates acceptance criteria before merge to `develop`.

## Forbidden Moves
- mixing multiple tasks in one branch without explicit approval
- merging unreviewed task work into `main`
- using branch names without task context when a task id exists
- rebasing or rewriting shared history after others rely on it
