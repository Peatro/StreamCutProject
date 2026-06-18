# TASK-083 Extend Operator Session Lifetime So Login Does Not Die Quickly

## Agent
backend-agent

## Summary
The operator's authenticated session expires too quickly in real use. Extend session longevity for the single-operator deployment: raise the servlet session timeout and add Spring Security's built-in persistent (remember-me) login so a backend restart or short idle period does not force a re-login.

## Context
From real operator use (Obsidian `Problems.md`): "Сессия пользователя быстро умирает". `SecurityConfiguration.java` uses `SessionCreationPolicy.IF_REQUIRED` with no explicit `server.servlet.session.timeout` (Spring Boot default 30m), and sessions are in-memory, so a backend restart drops the session entirely. For a single-operator tool this is pure friction.

## Problem Frame
- Symptom: operator gets logged out quickly / after backend restarts.
- Suspected Layer: backend security/session config (`SecurityConfiguration.java`, `application-local.yaml`, `application-prod.yaml`).
- Touched Contracts: none (auth model unchanged; same login form, same protected routes).
- Done Criterion: a logged-in operator stays authenticated across a long idle window and across a backend restart, without weakening auth for protected routes.

## Scope
- Set an explicit, generous `server.servlet.session.timeout` (e.g. `7d`) via config, overridable by env.
- Add Spring Security `rememberMe()` with a stable, configurable key (token-based / `TokenBasedRememberMeServices`) so the remember-me cookie survives a backend restart; cookie validity configurable (e.g. 30d) and overridable by env.
- Wire a remember-me checkbox/field into the existing `login.html` form if required by the chosen remember-me mechanism (parameter name matching Spring's expectation), keeping the login page styling intact.
- Keep secrets/keys out of source: provide via env with a documented local default, matching the existing `application-local.yaml` / `application-prod.yaml` split (prod must require an explicit key).

## Out of Scope
- No switch to JWT/stateless auth, no external session store (Redis), no OAuth — overkill for single-operator self-use.
- No change to which routes are protected or to the credential model.
- No multi-session / concurrent-session controls.

## Inputs
- `src/main/java/com/peatroxd/streamcutproject/config/SecurityConfiguration.java`
- `src/main/resources/application-local.yaml`, `src/main/resources/application-prod.yaml`
- `src/main/resources/static/login.html`
- `src/test/java/com/peatroxd/streamcutproject/config/SecurityConfigurationIntegrationTest.java`

## Touched Contracts
- none

## Schema Impact
- none (use token-based remember-me, not persistent-token DB table, to avoid a migration)

## Acceptance Criteria
- Explicit session timeout is configured and env-overridable; default is generous for single-operator use.
- Remember-me login is enabled with a configurable key; the remember-me cookie keeps the operator authenticated across a backend restart.
- Prod profile requires the remember-me key to be supplied explicitly (no insecure checked-in default for prod).
- Protected routes still require authentication for anonymous users; existing security integration test stays green (extend it to cover remember-me/timeout config if practical).

## Constraints
- Use Spring Security's built-in remember-me; do not hand-roll token crypto.
- No new infrastructure dependency (no Redis/DB session table).
- Keep keys/secrets out of source control; follow the existing local/prod profile split.
- No unrelated refactor of the security config.

## Expected Deliverables
- code
- tests
