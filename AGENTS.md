You are working on the repository duel-tower-main.

Read AGENTS.md first and follow it strictly.
Key constraints:
- Prefer offline/local work only.
- Use the repository's prescribed validation command.
- Do not enable internet unless absolutely necessary.
- If validation is blocked by missing offline dependencies, report the exact blocked domain or package instead of broadening access.

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