package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.RunState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SelectNodeChoiceCommand(
        UUID commandId,
        long expectedVersion,
        PlayerId playerId,
        String choiceId
) implements GameCommand {

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        if (playerId == null) {
            errors.add("playerId is required");
            return errors;
        }
        if (!state.players().containsKey(playerId)) {
            errors.add("player not found");
            return errors;
        }
        if (state.combat() != null || state.nodeState() == NodeState.COMBAT) {
            errors.add("cannot select node during combat");
        }
        if (choiceId == null || choiceId.isBlank()) {
            errors.add("choiceId is required");
            return errors;
        }

        RunState.NodeChoice choice = state.runState().findChoice(choiceId);
        if (choice == null) {
            errors.add("choice not found");
            return errors;
        }
        if (choice.disabled()) {
            errors.add(choice.disabledReason() == null ? "choice is disabled" : choice.disabledReason());
        }

        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        RunState.NodeChoice choice = state.runState().findChoice(choiceId);
        state.runState().select(choice, state.seed());

        if (choice.phase() == RunState.NodePhase.COMBAT) {
            state.nodeState(NodeState.COMBAT);
        } else {
            state.nodeState(NodeState.NON_COMBAT);
        }

        return List.of(new GameEvent.LogAppended("node selected: " + choice.name() + " (" + choice.typeLabel() + ")"));
    }
}
