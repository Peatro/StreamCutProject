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

INSTRUCTION:
Implement only the assigned task.
Do not expand scope.
Return a concise summary of changes and note any assumptions.
