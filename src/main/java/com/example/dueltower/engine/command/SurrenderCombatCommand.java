package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.combat.CombatStatuses;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.PlayerState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SurrenderCombatCommand(
        UUID commandId,
        long expectedVersion,
        Ids.PlayerId playerId,
        String reason
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
        }
        if (state.combat() == null || state.nodeState() != NodeState.COMBAT) {
            errors.add("surrender is only available during combat");
        }
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState player = state.player(playerId);
        if (player == null) {
            return List.of();
        }

        player.hp(0);
        player.statusSet(CombatStatuses.BATTLE_INCAPACITATED, 0);
        player.statusSet(CombatStatuses.BATTLE_INCAPACITATED_PERSISTENT, 1);

        String detail = (reason == null || reason.isBlank()) ? "" : " reason=" + reason.trim();
        return List.of(new GameEvent.LogAppended(playerId.value() + " surrenders combat." + detail));
    }
}
