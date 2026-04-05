# Global Rules

## Purpose
This repository uses orchestrated multi-agent development.
All agents must follow these rules strictly.

## Core Principles
- Follow the task scope exactly.
- Do not expand the task.
- Do not change architecture unless explicitly instructed.
- Do not introduce new frameworks or libraries without approval.
- Prefer simple and maintainable solutions.
- Keep responsibilities separated by module and role.

## General Execution Rules
- One task = one clear deliverable.
- No hidden side work.
- No opportunistic refactoring unless the task explicitly allows it.
- No changing unrelated files.
- No speculative abstractions.

## Communication Rules
- Be explicit.
- State assumptions clearly.
- If something is unclear, ask for clarification or document the ambiguity.
- Provide a short summary of what was changed.

## Code Change Rules
- Respect existing package and module boundaries.
- Do not move logic across layers without approval.
- Do not put business logic into controllers or UI layers.
- Do not access infrastructure details from unrelated modules.

## Dependency Rules
- Do not add a dependency unless it is necessary for the assigned task.
- If a new dependency is required, explain why the current stack is insufficient.

## Database Rules
- Do not change database schema outside the task scope.
- All DB changes must go through migrations.
- Do not rename or remove existing columns/tables unless explicitly required.

## Forbidden Actions
- Changing core architecture
- Renaming bounded contexts without approval
- Adding background behavior not requested by the task
- Combining multiple tasks into one implementation
- “Improving” unrelated code
