package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.combat.HealOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.RunState;
import com.example.dueltower.engine.model.TargetRef;
import com.example.dueltower.engine.model.TargetSelection;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record UseItemCommand(
        UUID commandId,
        long expectedVersion,
        Ids.PlayerId playerId,
        String itemId,
        int count,
        TargetSelection targets
) implements GameCommand {

    private static final Set<String> CLEANSABLE_STATUS_IDS = Set.of(
            "S101", // Pain
            "S102", // Stun
            "S103", // Pressure
            "S105", // Weak
            "S106", // Vulnerable
            "S107", // Confusion
            "S108"  // Seal
    );

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

        RunState.InventoryItem item = InventoryCommandSupport.findInventoryItem(state, itemId);
        if (item == null) {
            errors.add("item not found");
            return errors;
        }

        if (!item.battleUsable()) {
            errors.add("item is not battle usable");
        }
        if (item.count() < count) {
            errors.add("not enough item count");
        }

        if ("I-1".equals(item.id()) || "I-2".equals(item.id()) || "I-4".equals(item.id())) {
            List<Ids.PlayerId> targetPlayers = selectedTargetPlayersOrSelf();
            if (targetPlayers.isEmpty()) {
                errors.add("player target required");
            }
            for (Ids.PlayerId targetPlayerId : targetPlayers) {
                if (!state.players().containsKey(targetPlayerId)) {
                    errors.add("target player not found: " + targetPlayerId.value());
                }
            }
        }

        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        List<GameEvent> events = new ArrayList<>();

        RunState.InventoryItem item = InventoryCommandSupport.findInventoryItem(state, itemId);
        if (item == null) {
            return events;
        }

        InventoryCommandSupport.consumeInventoryItem(state, item, count);

        switch (item.id()) {
            case "I-1" -> applySmallPotion(state, ctx, events);
            case "I-2" -> applyAntidote(state, events);
            case "I-4" -> applySmokeBomb(state, events);
            default -> events.add(new GameEvent.LogAppended(playerId.value() + " uses item " + item.id() + " x" + count));
        }

        return events;
    }

    private void applySmallPotion(GameState state, EngineContext ctx, List<GameEvent> events) {
        for (Ids.PlayerId targetPlayerId : selectedTargetPlayersOrSelf()) {
            HealOps.apply(
                    state,
                    ctx,
                    events,
                    "item:" + itemId,
                    TargetRef.ofPlayer(targetPlayerId),
                    20 * count
            );
        }
    }

    private void applyAntidote(GameState state, List<GameEvent> events) {
        for (Ids.PlayerId targetPlayerId : selectedTargetPlayersOrSelf()) {
            PlayerState ps = state.player(targetPlayerId);
            if (ps == null) {
                continue;
            }
            boolean removed = false;
            for (String statusId : CLEANSABLE_STATUS_IDS) {
                if (ps.status(statusId) > 0) {
                    ps.statusSet(statusId, 0);
                    removed = true;
                    break;
                }
            }
            String outcome = removed ? "debuff removed" : "no debuff";
            events.add(new GameEvent.LogAppended("item:" + itemId + " used on " + targetPlayerId.value() + " (" + outcome + ")"));
        }
    }

    private void applySmokeBomb(GameState state, List<GameEvent> events) {
        for (Ids.PlayerId targetPlayerId : selectedTargetPlayersOrSelf()) {
            PlayerState ps = state.player(targetPlayerId);
            if (ps == null) {
                continue;
            }
            ps.statusAdd("S004", 2 * count);
            events.add(new GameEvent.LogAppended("item:" + itemId + " used on " + targetPlayerId.value() + " (+Evasion)"));
        }
    }

    private List<Ids.PlayerId> selectedTargetPlayersOrSelf() {
        List<Ids.PlayerId> selected = (targets == null) ? List.of() : targets.allPlayersOnly();
        if (selected.isEmpty()) {
            return List.of(playerId);
        }
        return List.copyOf(new LinkedHashSet<>(selected));
    }

}
