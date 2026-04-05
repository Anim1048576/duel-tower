package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record UnequipEquipmentCommand(
        UUID commandId,
        long expectedVersion,
        PlayerId playerId,
        String equipId
) implements GameCommand {
    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        if (playerId == null) {
            errors.add("playerId is required");
            return errors;
        }
        PlayerState player = state.player(playerId);
        if (player == null) {
            errors.add("player not found");
            return errors;
        }
        if (equipId == null || equipId.isBlank()) {
            errors.add("equipId is required");
            return errors;
        }
        if (state.combat() != null || state.nodeState() == NodeState.COMBAT) {
            errors.add("cannot unequip equipment during combat");
            return errors;
        }

        EquipDefinition def;
        try {
            def = ctx.equipDef(equipId.trim());
        } catch (IllegalArgumentException ex) {
            errors.add("equipment definition not found: " + equipId.trim());
            return errors;
        }

        EquippedItem equipped = player.equippedItem(def.slot());
        if (equipped == null || !def.id().equals(equipped.equipId())) {
            errors.add("equipment is not equipped");
        }
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState player = state.player(playerId);
        if (player == null) {
            return List.of();
        }
        EquipDefinition def = ctx.equipDef(equipId.trim());
        EquippedItem equipped = player.equippedItem(def.slot());
        if (equipped == null || !def.id().equals(equipped.equipId())) {
            return List.of();
        }

        player.unequipItem(def.slot());
        InventoryCommandSupport.addInventoryEntryCount(state, new EquipRef(def.id()), equipped.bound(), 1);

        return List.of(new GameEvent.LogAppended(playerId.value() + " 해제: " + def.id() + " [" + def.slot().name() + "]"));
    }
}
