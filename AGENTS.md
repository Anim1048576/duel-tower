# AGENTS.md

## Validation
Use this command for verification:

./gradlew --offline --no-daemon test

## Notes
- Do not enable internet during the agent phase unless absolutely necessary.
- Prefer cached dependencies from the setup step.
- If offline test fails due to missing dependencies, report the exact blocked/missing domain instead of broadening access immediately.