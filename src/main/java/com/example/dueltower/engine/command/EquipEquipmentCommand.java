package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record EquipEquipmentCommand(
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
            errors.add("cannot equip equipment during combat");
            return errors;
        }

        RunState.InventoryEntry entry = InventoryCommandSupport.findEquipEntry(state, equipId);
        if (entry == null) {
            errors.add("equipment not found");
            return errors;
        }

        EquipDefinition def;
        try {
            def = ctx.equipDef(((EquipRef) entry.ref()).equipId());
        } catch (IllegalArgumentException ex) {
            errors.add("equipment definition not found: " + ((EquipRef) entry.ref()).equipId());
            return errors;
        }

        if (entry.count() < 1) {
            errors.add("not enough equipment count");
        }
        if (player.equippedItem(def.slot()) != null) {
            errors.add("slot already occupied: " + def.slot().name());
        }
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState player = state.player(playerId);
        RunState.InventoryEntry entry = InventoryCommandSupport.findEquipEntry(state, equipId);
        if (player == null || entry == null) {
            return List.of();
        }
        EquipDefinition def = ctx.equipDef(((EquipRef) entry.ref()).equipId());

        InventoryCommandSupport.consumeInventoryEntry(state, entry, 1);
        player.equipItem(def.slot(), new EquippedItem(def.id(), entry.bound()));

        return List.of(new GameEvent.LogAppended(playerId.value() + " 장착: " + def.id() + " [" + def.slot().name() + "]"));
    }
}
