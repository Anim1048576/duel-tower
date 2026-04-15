# Duel Tower UI

Svelte + TypeScript + Vite frontend for Duel Tower.

## Setup

Run all commands from `duel-tower-ui/`.

```bash
npm ci
```

## Common Commands

```bash
npm test
npm run check
npm run build
```

- `npm test`: runs frontend tests under `tests/**/*.test.js`
- `npm run test:unit`: alias for `npm test`
- `npm run test:watch`: reruns the same tests in watch mode
- `npm run check`: runs Svelte and TypeScript checks
- `npm run build`: creates the production bundle
- `npm run dev -- --host 127.0.0.1`: starts the local Vite dev server

## DeckEditor Verification

After changing DeckEditor screen, local editor state, or Screen API consumption, run:

```bash
npm test
npm run check
npm run build
```

Current frontend tests cover DeckEditor presentation state and validation freshness. Add new `*.test.js` files under `tests/` to include them automatically in `npm test`.

## PresetEditor Verification

After changing PresetEditor screen contracts, local presentation helpers, or editor screen actions, run:

```bash
npm test
npm run check
npm run build
```

Current frontend tests cover:
- PresetEditor local presentation and dirty/preview reactions
- Editor screen action orchestration and refresh flow
