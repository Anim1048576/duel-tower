package com.example.dueltower.engine.command;

import com.example.dueltower.content.equip.edb.EquipIds;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.combat.DamageOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record UseEquipActionCommand(
        UUID commandId,
        long expectedVersion,
        PlayerId playerId,
        String inventoryEquipId,
        TargetSelection selection
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
        if (def.action() == null) {
            errors.add("equipment action not available");
            return errors;
        }
        if (def.action().target() == Target.ENEMY_ONE) {
            if (selection == null || selection.targets() == null || selection.targets().size() != 1) {
                errors.add("exactly one enemy target is required");
            } else if (!(selection.targets().get(0) instanceof TargetRef.Enemy enemy) || state.enemy(enemy.id()) == null) {
                errors.add("enemy target required");
            }
        }
        int loadedAmmo = equipped.loadedAmmo() == null ? 0 : equipped.loadedAmmo();
        if (def.action().requiresLoadedAmmo() && loadedAmmo < def.action().loadedAmmoCost()) {
            errors.add("not enough loaded ammo");
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
        if (def.action() == null || !EquipIds.PORTABLE_PISTOL.equals(def.id())) {
            return List.of();
        }
        TargetRef.Enemy enemy = (TargetRef.Enemy) selection.targets().get(0);
        List<GameEvent> events = new ArrayList<>();
        DamageOps.apply(
                state,
                ctx,
                events,
                TargetRef.ofPlayer(playerId),
                playerId.value(),
                TargetRef.ofEnemy(enemy.id()),
                12
        );
        player.equipItem(def.slot(), new EquippedItem(
                equipped.inventoryEquipId(),
                equipped.equipId(),
                equipped.bound(),
                Math.max(0, (equipped.loadedAmmo() == null ? 0 : equipped.loadedAmmo()) - def.action().loadedAmmoCost()),
                equipped.maxLoadedAmmo()
        ));
        events.add(new GameEvent.LogAppended(playerId.value() + " 장비 액션 사용: " + equipped.equipId() + " #" + equipped.inventoryEquipId()));
        return events;
    }
}
