package com.example.dueltower.engine.core.effect.item;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.List;

public record UseItemResolutionContext(
        EngineContext ctx,
        GameState state,
        RunState run,
        CombatState combat,
        PlayerState actor,
        RunState.InventoryEntry inventoryItem,
        int useCount,
        String targetCharacterId,
        List<GameEvent> out
) {
}
