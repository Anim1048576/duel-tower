package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.item.ItemEffect;
import com.example.dueltower.engine.core.effect.item.UseItemResolutionContext;
import com.example.dueltower.engine.core.effect.item.UseItemValidationContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record UseItemCommand(
        UUID commandId,
        long expectedVersion,
        Ids.PlayerId playerId,
        String itemId,
        int count,
        TargetSelection targets
) implements GameCommand {

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();

        PlayerState actor = CommandValidation.validateMainTurn(state, playerId, errors);
        if (actor == null) {
            return errors;
        }

        if (itemId == null || itemId.isBlank()) {
            errors.add("itemId is required");
            return errors;
        }

        if (count <= 0) {
            errors.add("count must be >= 1");
        }

        RunState.InventoryEntry item = InventoryCommandSupport.findItemEntry(state, itemId);
        if (item == null) {
            errors.add("item not found");
            return errors;
        }

        ItemDefinition definition;
        try {
            definition = ctx.itemDef(((ItemRef) item.ref()).itemId());
        } catch (IllegalArgumentException ex) {
            errors.add("item definition not found: " + ((ItemRef) item.ref()).itemId());
            return errors;
        }

        if (!definition.battleUsable()) {
            errors.add("item is not battle usable");
        }
        if (item.count() < count) {
            errors.add("not enough item count");
        }

        ItemEffect effect;
        try {
            effect = ctx.itemEffect(((ItemRef) item.ref()).itemId());
        } catch (IllegalArgumentException ex) {
            errors.add("item effect not found: " + ((ItemRef) item.ref()).itemId());
            return errors;
        }

        String targetCharacterId = targetCharacterIdOrNull();
        if (targetCharacterId != null && !state.players().containsKey(new Ids.PlayerId(targetCharacterId))) {
            errors.add("target player not found: " + targetCharacterId);
        }

        if (effect.requiresTarget() && targetCharacterId == null) {
            errors.add("player target required");
        }

        UseItemValidationContext vc = new UseItemValidationContext(
                ctx,
                state,
                state.runState(),
                state.combat(),
                actor,
                item,
                count,
                targetCharacterId
        );
        effect.validateUse(vc, errors);

        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        List<GameEvent> events = new ArrayList<>();

        RunState.InventoryEntry item = InventoryCommandSupport.findItemEntry(state, itemId);
        if (item == null) {
            return events;
        }

        ItemEffect effect = ctx.itemEffect(((ItemRef) item.ref()).itemId());

        InventoryCommandSupport.consumeInventoryEntry(state, item, count);

        PlayerState actor = state.player(playerId);
        if (actor == null) {
            return events;
        }

        UseItemResolutionContext rc = new UseItemResolutionContext(
                ctx,
                state,
                state.runState(),
                state.combat(),
                actor,
                item,
                count,
                targetCharacterIdOrNull(),
                events
        );
        effect.resolveUse(rc);

        return events;
    }

    private String targetCharacterIdOrNull() {
        if (targets == null) {
            return null;
        }
        List<Ids.PlayerId> selected = targets.allPlayersOnly();
        if (selected.isEmpty()) {
            return null;
        }
        return selected.get(0).value();
    }
}
