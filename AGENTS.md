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
- Preferred validation command:
  ./gradlew --offline --no-daemon test
- If offline validation fails because required Gradle artifacts are not present in the local cache, do not switch the normal validation flow to online.
- Instead, report the exact missing artifact coordinates and/or blocked repository domain.
- If a dedicated environment setup script is available, it may pre-warm dependencies with a command such as:
  ./gradlew --no-daemon test --refresh-dependencies || true
- After any approved pre-warm/setup step, return to the prescribed offline validation command.

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
