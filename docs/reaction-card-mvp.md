# Reaction Card MVP

This MVP implements reaction cards only as post-resolution follow-up actions.

- A reaction card never cancels, redirects, rewinds, reduces, or increases the original action.
- No pre-damage, pre-effect, stack, interrupt, priority, or chain system is implemented.
- The only implemented trigger is `AFTER_ENEMY_ATTACK_DAMAGED_SELF`.
- The server opens a `REACTION_CARD` `PendingDecision` after an enemy attack has fully resolved and a player actually took damage.
- The player resolves the pending decision with `RESOLVE_REACTION`, using `cardId` for a reaction card or `null` to skip.
- Events caused while resolving a reaction do not open another reaction pending decision.

Current MVP card:

- `C005` / `긴급 공격`
- Normal use: costs 1 AP and deals full attack power damage to one enemy.
- Reaction use: costs no AP, does not count as a played card, and deals `floor(attackPower / 2)` to the enemy that just attacked.
