# Workflow

## Development Model
This project uses:
- one orchestrator agent
- multiple worker agents
- one reviewer/QA loop
- lightweight GitFlow with `main` and `develop`

## Task Lifecycle
1. Orchestrator selects task
2. Orchestrator loads:
   - global rules
   - architecture
   - agent role file
   - task file
   - related contracts
3. Orchestrator assigns task to the correct worker agent
4. Worker implements only task scope
5. Reviewer agent checks the result
6. QA agent validates acceptance criteria where needed
7. Orchestrator closes or reopens the task

## Rules
- A task is not complete until acceptance criteria are satisfied.
- Reviewer cannot silently redefine the task.
- QA cannot introduce feature scope.
- Orchestrator cannot write implementation code.
- Implementation should happen in task branches, not directly in `main`.
- Completed task work should merge into `develop` before any stable merge to `main`.

## Task Assignment Rule
One task should map to one primary agent role.
If a task requires multiple roles, split it first.

## Branching Rule
Use the branch workflow defined in `git-workflow.md`.
Prefer branch names in the form `feature/TASK-XXX-short-name` or `fix/TASK-XXX-short-name`.
