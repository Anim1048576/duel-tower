You are working on the repository duel-tower-main.

Read AGENTS.md first and follow it strictly.
Key constraints:
- Prefer offline/local work only.
- Use the repository's prescribed validation command.
- Do not enable internet unless absolutely necessary.
- If validation is blocked by missing offline dependencies, report the exact blocked domain or package instead of broadening access.
- Do not change repositories/dependency declarations only to work around a cache miss unless the task explicitly requires it.
- If this environment supports a separate setup/prebuild step, prefer warming Gradle dependencies there rather than enabling network access during normal task execution.

Offline dependency policy:
- Default validation is offline-first.
- Normal validation command:
  ./gradlew --offline --no-daemon test

Validation order for Codex or CI-like environments:
1. If the environment provides a dedicated setup/prebuild step, use that step only to warm Gradle dependencies.
2. Preferred dependency warm-up command:
   ./gradlew --no-daemon test --refresh-dependencies || true
3. After the warm-up step, return to the normal validation command:
   ./gradlew --offline --no-daemon test
4. Do not replace the normal validation flow with online Gradle test execution.
5. If offline validation still fails because artifacts are missing, report the exact missing artifact coordinates and/or blocked repository domain (for example `repo.maven.apache.org`).

Do not modify dependency declarations only to bypass an offline cache miss.

Global goals:
- Keep the current architecture style.
- Favor small, safe, reviewable changes.
- Do not do unrelated refactors.
- Do not remove existing behavior unless required by the task.
- Preserve Korean comments and existing naming style where reasonable.

When you finish each task, provide:
1) a short summary of what changed
2) files changed
3) validation command(s) run and result
4) remaining risks or follow-up items

Unless the task explicitly says otherwise, validate with:
./gradlew --offline --no-daemon test
