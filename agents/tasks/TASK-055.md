# TASK-055 Add Authentication And Protected Operator Access

## Agent
backend-agent

## Summary
Introduce a small-team authentication model so the operator UI and write APIs are no longer publicly exposed.

## Context
The current MVP has no access control. That is acceptable for local validation, but not for a finished `v1.0.0` service.

## Scope
- choose and implement a minimal authentication model suitable for a self-hosted small team
- protect operator pages and state-changing APIs
- protect artifact access if artifacts remain operator-facing
- document bootstrap and operator login flow
- add tests for authenticated versus unauthenticated access

## Out of Scope
- SSO
- OAuth provider integrations
- multi-tenant workspaces
- granular enterprise RBAC

## Inputs
- backlog.md
- runtime.md
- agents/contracts/api-contracts.md
- src/main/java/com/peatroxd/streamcutproject
- src/main/resources/static

## Expected Deliverables
- code
- tests
- docs

## Constraints
- keep the auth model operationally simple
- preserve current operator workflow as much as possible
- do not introduce a heavyweight identity stack for `v1.0.0`

## Acceptance Criteria
- unauthenticated browser access to protected pages is redirected or denied consistently
- unauthenticated API access to protected endpoints fails with stable auth semantics
- authenticated operators can still complete the core service workflow end-to-end
- the authentication bootstrap, credential policy, and configuration path are documented
- the chosen auth model is explicit in runtime and release docs

## Notes
One clearly documented operator role is sufficient for `v1.0.0` if it protects the service and avoids fake complexity.

## Implementation Notes
- Auth shape: Spring Security form login + session cookie.
- CSRF bootstrap: public `/csrf` endpoint plus `X-XSRF-TOKEN` on operator POSTs from the static app.
- Public surfaces retained: `/health` and `/api/internal/worker/**`.
- Follow-up hardening for machine auth can move to `TASK-056` if needed.

## Verified On 2026-04-06
- Focused security integration test passed: `./gradlew test --tests com.peatroxd.streamcutproject.config.SecurityConfigurationIntegrationTest --no-configuration-cache`
- Full backend test suite passed: `./gradlew test --no-configuration-cache`
- Live Docker smoke confirmed:
  - `/health` remained public
  - `/index.html` redirected to `/login.html` without an operator session
  - `/login.html` loaded successfully
  - `/csrf` returned a token and `XSRF-TOKEN` cookie
  - authenticated session access to `/api/jobs` returned `200`

## Residual Notes
- Operator logout UX is still minimal and can be improved later without changing the auth model.
- Worker transport remains public in `TASK-055` by design; stronger machine-auth hardening should move to `TASK-056`.
