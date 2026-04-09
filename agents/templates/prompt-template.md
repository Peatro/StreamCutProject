# Prompt Assembly Template

SYSTEM CONTEXT:
- global/rules.md
- global/architecture.md
- global/coding-standards.md
- global/anti-patterns.md

ROLE CONTEXT:
- roles/<agent-role>.md

TASK CONTEXT:
- tasks/TASK-XXX.md

OPTIONAL CONTRACT CONTEXT:
- contracts/data-models.md
- contracts/state-machine.md
- contracts/worker-protocol.md
- contracts/api-contracts.md

ASSEMBLY RULES:
- always include the role file and task file
- if the task touches contracts or schema, include the corresponding contract files explicitly
- if the task changes orchestration behavior, include `data-models.md`, `state-machine.md`, `worker-protocol.md`, and `api-contracts.md`
- if the task changes migrations or persistence semantics, call out schema impact directly in the prompt

INSTRUCTION:
Implement only the assigned task.
Do not expand scope.
Return a concise summary of changes and note any assumptions.
