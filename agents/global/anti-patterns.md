# Anti-Patterns

## Avoid These
- Overengineering
- Premature optimization
- Excessive layering without benefit
- Generic abstractions with one implementation
- Mixing transport DTOs with domain models
- Controllers containing business logic
- Worker code directly writing to DB
- UI depending on unstable internal backend formats
- Doing extra work not requested by the task

## Common Agent Failure Modes
- “I also refactored several unrelated modules”
- “I changed the architecture to make it cleaner”
- “I added a generic base class hierarchy”
- “I introduced a new dependency because it is more modern”
- “I renamed some modules for consistency”

## Correct Behavior
- Finish the exact task
- Keep changes local
- Respect contracts
- Document assumptions
