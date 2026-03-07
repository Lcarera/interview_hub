---
name: pr-check
description: Run backend tests + coverage and frontend tests locally before creating a PR
disable-model-invocation: true
---

# Pre-PR Check

Run the same checks that CI runs on pull requests, locally.

## Steps

1. Run backend and frontend checks in parallel:
   - Backend: `./gradlew check` (compiles, runs tests, JaCoCo coverage verification)
   - Frontend: `cd frontend && bun run test` (Vitest unit tests)
2. Report results:
   - If both pass: confirm ready to create PR
   - If either fails: show the failure output and suggest fixes
3. Optionally, if the user confirms, proceed to create the PR using the `commit-commands:commit-push-pr` skill.
