package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.model.*;

public final class EquipmentCombatOps {
    private EquipmentCombatOps() {}

    public static int attackPowerBonus(PlayerState player, EngineContext ctx) {
        if (player == null) {
            return 0;
        }
        return player.equippedItems().values().stream()
                .mapToInt(item -> combatModifier(item, ctx).attackPowerBonus())
                .sum();
    }

    public static int incomingDamageBonus(GameState state, TargetRef target, EngineContext ctx) {
        if (!(target instanceof TargetRef.Player p)) {
            return 0;
        }
        PlayerState player = state.player(p.id());
        if (player == null) {
            return 0;
        }
        return player.equippedItems().values().stream()
                .mapToInt(item -> combatModifier(item, ctx).incomingDamageBonus())
                .sum();
    }

    private static EquipCombatModifierDefinition combatModifier(EquippedItem equipped, EngineContext ctx) {
        if (ctx == null || equipped == null || !ctx.hasEquipDef(equipped.equipId())) {
            return new EquipCombatModifierDefinition(0, 0);
        }
        EquipCombatModifierDefinition modifier = ctx.equipDef(equipped.equipId()).combatModifier();
        return modifier == null ? new EquipCombatModifierDefinition(0, 0) : modifier;
    }
}
