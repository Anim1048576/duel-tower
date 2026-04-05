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
        String inventoryEquipId
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
        if (inventoryEquipId == null || inventoryEquipId.isBlank()) {
            errors.add("inventoryEquipId is required");
            return errors;
        }
        if (state.combat() != null || state.nodeState() == NodeState.COMBAT) {
            errors.add("cannot unequip equipment during combat");
            return errors;
        }

        EquippedItem equipped = player.equippedItems().values().stream()
                .filter(item -> inventoryEquipId.trim().equals(item.inventoryEquipId()))
                .findFirst()
                .orElse(null);
        if (equipped == null) {
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
        EquippedItem equipped = player.equippedItems().values().stream()
                .filter(item -> inventoryEquipId.trim().equals(item.inventoryEquipId()))
                .findFirst()
                .orElse(null);
        if (equipped == null) {
            return List.of();
        }

        EquipDefinition def = ctx.equipDef(equipped.equipId());
        player.unequipItem(def.slot());
        InventoryCommandSupport.addEquipInventoryEntry(
                state,
                equipped.inventoryEquipId(),
                def.id(),
                equipped.bound(),
                equipped.loadedAmmo(),
                equipped.maxLoadedAmmo()
        );

        return List.of(new GameEvent.LogAppended(playerId.value() + " 해제: " + def.id() + " [" + def.slot().name() + "] #" + equipped.inventoryEquipId()));
    }
}
