# TASK-056 Add Security Baseline And Input Hardening

## Agent
backend-agent

## Summary
Add the minimum security posture and input hardening expected from a real operator-facing service.

## Context
After authentication exists, the service still needs predictable rejection of malformed or hostile inputs and a basic HTTP security baseline.

## Scope
- harden upload, URL ingest, and artifact-related inputs
- validate and sanitize user-controlled filenames, paths, and remote source inputs
- align CSRF, session, CORS, and security-header behavior with the chosen auth model
- ensure dangerous or malformed requests fail with stable, readable errors
- document the security posture and known tradeoffs

## Out of Scope
- WAF deployment
- advanced rate-limiting infrastructure
- penetration testing program

## Inputs
- TASK-055.md
- runtime.md
- agents/contracts/api-contracts.md
- src/main/java/com/peatroxd/streamcutproject

## Expected Deliverables
- code
- tests
- docs

## Constraints
- follow the auth model established by `TASK-055`
- prefer explicit validation over ad-hoc defensive code
- keep the scope to practical service hardening

## Acceptance Criteria
- common malformed or dangerous inputs are rejected predictably
- HTTP security behavior is documented and consistent with the deployed auth flow
- security-sensitive request paths have focused automated coverage
- no major write path still depends on implicit framework defaults alone

## Notes
If any important gap is intentionally deferred, it must be written down as a known limitation rather than left implicit.
