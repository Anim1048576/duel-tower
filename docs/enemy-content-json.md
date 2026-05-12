# Enemy Content JSON Guide

This guide describes the current JSON-based enemy content flow.

## Overview

- Enemy definitions live in `src/main/resources/balance/enemies.json`.
- Encounter placement lives in `src/main/resources/balance/encounters.json`.
- `EnemyDefinition` is static content loaded from JSON.
- `EnemyState` is runtime combat state created from an `EnemyDefinition`.
- AI, automatic enemy turns, enemy card draw/shuffle/use, intent UI, DB content storage, and YAML loading are not implemented in this version.

## enemies.json

Top-level shape:

```json
{
  "enemies": []
}
```

Each enemy entry uses:

- `id`: unique enemy definition ID, for example `E002_TOWER_RAT`
- `name`: display name
- `role`: one of `NORMAL`, `ELITE`, `BOSS`, `EVENT`
- `description`: optional text; `null` loads as an empty string
- `stats.maxHp`: must be greater than 0
- `stats.maxActionPoint`: must be 0 or greater
- `stats.attackPower`: must be 0 or greater
- `stats.healPower`: must be 0 or greater
- `deck`: list of cdb card IDs; currently validated only, not used by enemy card logic
- `startStatuses`: list of `{ "statusId": "...", "stacks": 1 }`
- `passives`: list of `{ "passiveId": "..." }`

`deck`, `startStatuses`, and `passives` may be empty arrays. Referenced card, status, and passive IDs must already exist in their content services.

## encounters.json

Top-level shape:

```json
{
  "fallbackEncounterId": "RUN-DEFAULT-COMBAT",
  "encounters": []
}
```

Each encounter entry uses:

- `encounterId`: unique encounter ID
- `minFloor`: optional lower floor bound
- `maxFloor`: optional upper floor bound
- `requiredNodePhase`: optional node phase, such as `COMBAT`
- `enemies`: one or more enemy placement templates

Each enemy template uses:

- `enemyDefId`: ID of the enemy definition to instantiate, for example `E002_TOWER_RAT`
- `instanceId`: runtime enemy instance ID inside this encounter, for example `RUN-ENEMY-1`
- `hpPerFloor`: optional floor scaling, defaults to 0
- `attackPowerPerFloor`: optional floor scaling, defaults to 0
- `healingPowerPerFloor`: optional floor scaling, defaults to 0

`instanceId` must be unique within a single encounter.

## Definition vs State

`EnemyDefinition` is static content. It does not change during combat.

`EnemyState` is runtime state. It stores current HP/AP, statuses, passive IDs, and combat-specific values. It is created from:

- encounter `instanceId`
- encounter floor scaling
- the referenced `EnemyDefinition`

## Adding an Enemy

1. Add an entry to `balance/enemies.json`.
2. Use a unique `id`.
3. Keep all stats non-negative, with `maxHp > 0`.
4. Add only existing card/status/passive IDs to `deck`, `startStatuses`, and `passives`.
5. Run `./gradlew test`.

## Adding an Encounter

1. Add an entry to `balance/encounters.json`.
2. Use `enemyDefId` to reference an enemy in `enemies.json`.
3. Use unique `instanceId` values inside the encounter.
4. Put base stats in `enemies.json`; put only floor scaling in `encounters.json`.
5. Run `./gradlew test`.

## Common Errors

- Duplicate enemy ID: use a unique `enemies[].id`.
- Invalid role: use only `NORMAL`, `ELITE`, `BOSS`, or `EVENT`.
- Blank enemy name: fill `enemies[].name`.
- Missing card/status/passive reference: add valid existing content IDs.
- `startStatuses.stacks <= 0`: use a positive stack count.
- Blank `enemyDefId` or `instanceId`: fill both fields.
- Missing encounter enemy definition: add the enemy to `enemies.json` or fix `enemyDefId`.
- Duplicate encounter `instanceId`: rename one instance inside the encounter.
- Missing fallback encounter: make `fallbackEncounterId` match an existing `encounterId`.

## Current Limits

- Enemy `deck` is validated but not drawn, shuffled, or used.
- Enemy passives are copied to `EnemyState.passiveIds`, but new enemy-specific AI behavior is not implemented here.
- Start statuses are applied to `EnemyState.statusValues` when combat starts.
- Boss phase handling and intent UI are outside this version.
