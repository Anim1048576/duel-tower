package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.model.PlayerState;

public final class CombatStatuses {
    private CombatStatuses() {}

    /** 전투 중 덱/묘지 고갈로 인한 [전투 불능] (전투 한정). */
    public static final String BATTLE_INCAPACITATED = "BATTLE_INCAPACITATED";
    /** HP 0 도달로 인한 [전투 불능] (전투 간 지속). */
    public static final String BATTLE_INCAPACITATED_PERSISTENT = "BATTLE_INCAPACITATED_PERSISTENT";

    /** 현재 전투 기준 행동 불가 여부(전투 한정 + 지속형 모두 포함). */
    public static boolean isBattleIncapacitated(PlayerState ps) {
        return ps != null
                && (ps.status(BATTLE_INCAPACITATED) > 0
                || ps.status(BATTLE_INCAPACITATED_PERSISTENT) > 0);
    }

    /** 전투 간에도 유지되는 [전투 불능] 여부. */
    public static boolean isPersistentlyBattleIncapacitated(PlayerState ps) {
        return ps != null && ps.status(BATTLE_INCAPACITATED_PERSISTENT) > 0;
    }
}
