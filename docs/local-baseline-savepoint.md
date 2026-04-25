# Local Baseline Save Point

This document records the current local validation baseline before starting the next change.
Commands are written relative to the repository root and do not depend on a personal local path.

## Recorded At

- Date: 2026-04-25
- Scope: backend test plus available frontend check, test, and build commands
- Code changes included in this baseline: none

## Commands Run

### Backend

```bash
./gradlew test
```

Result: passed.

### Frontend

```bash
npm --prefix duel-tower-ui run check
npm --prefix duel-tower-ui run test
npm --prefix duel-tower-ui run build
```

Results:

- `npm --prefix duel-tower-ui run check`: passed, `svelte-check` reported 0 errors and 0 warnings.
- `npm --prefix duel-tower-ui run test`: passed, frontend test runner completed 13 test files.
- `npm --prefix duel-tower-ui run build`: passed, Vite production build completed.

## Current Failure Status

No backend or frontend validation failures were observed in this baseline run.

## Points To Check If This Baseline Fails Later

- Backend `./gradlew test`
  - Check whether dependency resolution is blocked by the environment before changing project dependencies.
  - Check the first failing test class and assertion message instead of reviewing the full Gradle log.
  - If failures involve local infrastructure, verify required runtime assumptions separately from unit or integration test assertions.

- Frontend `npm --prefix duel-tower-ui run check`
  - Check Svelte diagnostics and TypeScript errors first.
  - Confirm generated or derived types still match API payload changes.

- Frontend `npm --prefix duel-tower-ui run test`
  - Check the first failing test file and the named scenario.
  - Prioritize failures around recently changed screen state, deck editing, or combat action flow code.

- Frontend `npm --prefix duel-tower-ui run build`
  - Check Vite or Svelte compile errors before inspecting bundle output.
  - The build writes production UI assets under `src/main/resources/static/ui/`; verify whether any resulting tracked asset change is intentional.

## Recommended Pre-Work Check

Before starting a new task, run the same command set and compare against this save point:

```bash
./gradlew test
npm --prefix duel-tower-ui run check
npm --prefix duel-tower-ui run test
npm --prefix duel-tower-ui run build
```

If a command fails, record only the command, failing test or diagnostic name, core error message, and likely cause.
