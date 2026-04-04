package com.example.dueltower.engine.core.effect.item;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.model.*;

public record UseItemValidationContext(
        EngineContext ctx,
        GameState state,
        RunState run,
        CombatState combat,
        PlayerState actor,
        RunState.InventoryItem inventoryItem,
        int useCount,
        String targetCharacterId
) {
}
