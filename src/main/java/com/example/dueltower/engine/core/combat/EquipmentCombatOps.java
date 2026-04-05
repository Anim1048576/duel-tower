package com.example.dueltower.engine.core.combat;

import com.example.dueltower.content.equip.edb.EquipIds;
import com.example.dueltower.engine.model.EquippedItem;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.PlayerState;

public final class EquipmentCombatOps {
    private EquipmentCombatOps() {}

    public static int attackPowerBonus(PlayerState player) {
        if (hasEquipped(player, EquipIds.STURDY_SPEAR)) {
            return 2;
        }
        return 0;
    }

    public static int incomingDamageBonus(GameState state, com.example.dueltower.engine.model.TargetRef target) {
        if (!(target instanceof com.example.dueltower.engine.model.TargetRef.Player p)) {
            return 0;
        }
        PlayerState player = state.player(p.id());
        if (player == null) {
            return 0;
        }
        if (hasEquipped(player, EquipIds.STURDY_SPEAR)) {
            return 1;
        }
        return 0;
    }

    private static boolean hasEquipped(PlayerState player, String equipId) {
        return player.equippedItems().values().stream().map(EquippedItem::equipId).anyMatch(equipId::equals);
    }
}
