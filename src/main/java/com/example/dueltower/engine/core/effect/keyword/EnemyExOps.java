package com.example.dueltower.engine.core.effect.keyword;

import com.example.dueltower.engine.model.EnemyState;

/**
 * Enemy EX activation gate.
 *
 * Rule: enemy EX is activatable only when all of the following are true:
 * - enemy has an EX card assigned
 * - EX is not on cooldown
 * - status "BOSS_EX_READY" is present (>0)
 */
public final class EnemyExOps {
    private EnemyExOps() {}

    public static final String BOSS_EX_READY = "BOSS_EX_READY";

    public static void refreshActivatable(EnemyState enemy, int round) {
        if (enemy == null) return;

        boolean enabled = enemy.exCard() != null
                && !enemy.exOnCooldown(round)
                && enemy.status(BOSS_EX_READY) > 0;
        enemy.exActivatable(enabled);
    }
}
