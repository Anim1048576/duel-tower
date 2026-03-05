package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.model.PlayerState;

public final class CombatStatuses {
    private CombatStatuses() {}

    public static final String BATTLE_INCAPACITATED = "BATTLE_INCAPACITATED";

    public static boolean isBattleIncapacitated(PlayerState ps) {
        return ps != null && ps.status(BATTLE_INCAPACITATED) > 0;
    }
}

