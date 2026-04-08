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
3. Orchestrator writes a short Problem Frame:
   - symptom
   - suspected layer
   - touched contracts
   - done criterion
4. Orchestrator confirms whether the task touches contracts, schema, orchestration, or runtime behavior
5. If the task crosses multiple domains, orchestrator splits it before assignment
6. Worker implements only task scope
7. Reviewer agent checks the result
8. QA agent validates acceptance criteria where needed
9. Orchestrator closes or reopens the task

## Rules
- a task is not complete until acceptance criteria are satisfied
- reviewer cannot silently redefine the task
- QA cannot introduce feature scope
- orchestrator cannot write implementation code
- implementation should happen in task branches, not directly in `main`
- completed task work should merge into `develop` before any stable merge to `main`
- contract-aware tasks must explicitly name the affected contract files

## Task Assignment Rule
One task should map to one primary agent role.
If a task requires multiple roles, split it first.

## Contract-Aware Assignment Rule
Every implementation-ready task packet should include a short Problem Frame:
- symptom
- suspected layer
- touched contracts
- done criterion

If a task changes contracts or schema, the task packet must explicitly include:
- touched contract files
- schema impact
- migration or rollback note if applicable

If a task changes orchestration behavior, the task packet must include:
- `contracts/data-models.md`
- `contracts/state-machine.md`
- `contracts/worker-protocol.md`
- `contracts/api-contracts.md`

## Branching Rule
Use the branch workflow defined in `git-workflow.md`.
Prefer branch names in the form `feature/TASK-XXX-short-name` or `fix/TASK-XXX-short-name`.
