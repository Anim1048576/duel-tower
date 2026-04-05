package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.EquipDefinition;
import com.example.dueltower.engine.model.EquippedItem;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.RunState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ReloadEquipmentCommand(
        UUID commandId,
        long expectedVersion,
        PlayerId playerId,
        String inventoryEquipId
) implements GameCommand {
    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        PlayerState player = CommandValidation.validateMainTurn(state, playerId, errors);
        if (player == null) {
            return errors;
        }
        if (inventoryEquipId == null || inventoryEquipId.isBlank()) {
            errors.add("inventoryEquipId is required");
            return errors;
        }
        EquippedItem equipped = player.equippedItems().values().stream()
                .filter(item -> inventoryEquipId.trim().equals(item.inventoryEquipId()))
                .findFirst()
                .orElse(null);
        if (equipped == null) {
            errors.add("equipment is not equipped");
            return errors;
        }
        EquipDefinition def = ctx.equipDef(equipped.equipId());
        if (def.ammoPolicy() == null || def.reloadPolicy() == null) {
            errors.add("reload is not available for this equipment");
            return errors;
        }
        int loadedAmmo = equipped.loadedAmmo() == null ? 0 : equipped.loadedAmmo();
        int maxLoadedAmmo = equipped.maxLoadedAmmo() == null ? def.ammoPolicy().maxLoadedAmmo() : equipped.maxLoadedAmmo();
        if (loadedAmmo >= maxLoadedAmmo) {
            errors.add("loaded ammo is already full");
        }
        RunState.InventoryEntry ammoBundle = InventoryCommandSupport.findItemEntry(state, def.reloadPolicy().ammoItemId());
        if (ammoBundle == null || ammoBundle.count() <= 0) {
            errors.add(def.reloadPolicy().ammoItemId() + " is required");
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
        if (def.ammoPolicy() == null || def.reloadPolicy() == null) {
            return List.of();
        }
        RunState.InventoryEntry ammoBundle = InventoryCommandSupport.findItemEntry(state, def.reloadPolicy().ammoItemId());
        if (ammoBundle == null) {
            return List.of();
        }
        InventoryCommandSupport.consumeInventoryEntry(state, ammoBundle, 1);
        int maxLoadedAmmo = equipped.maxLoadedAmmo() == null ? def.ammoPolicy().maxLoadedAmmo() : equipped.maxLoadedAmmo();
        int currentLoadedAmmo = equipped.loadedAmmo() == null ? 0 : equipped.loadedAmmo();
        int nextLoadedAmmo = Math.min(maxLoadedAmmo, currentLoadedAmmo + def.reloadPolicy().ammoPerReload());
        player.equipItem(
                def.slot(),
                new EquippedItem(equipped.inventoryEquipId(), equipped.equipId(), equipped.bound(), nextLoadedAmmo, maxLoadedAmmo)
        );
        return List.of(new GameEvent.LogAppended(playerId.value() + " 장전 완료: " + equipped.equipId() + " #" + equipped.inventoryEquipId()));
    }
}
