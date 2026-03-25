You are working on the repository duel-tower-main.

Read AGENTS.md first and follow it strictly.
Key constraints:
- Prefer local work first.
- Use the repository's prescribed validation command.
- Do not enable internet unless the task requires it.
- Do not change repositories/dependency declarations only to work around environment issues unless the task explicitly requires it.

Validation policy:
- Default validation command:
  ./gradlew test
- If the environment blocks dependency download or network access, report the exact blocked domain or package.
- Do not switch to offline-only validation unless the task explicitly requires it.

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
./gradlew test