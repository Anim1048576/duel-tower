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
        state.runState().beginNode(choice);

        List<GameEvent> events = new ArrayList<>();
        events.add(new GameEvent.LogAppended("노드 선택: " + choice.name() + " (" + choice.typeLabel() + ")"));
        if (choice.phase() == RunState.NodePhase.COMBAT) {
            state.nodeState(NodeState.COMBAT);
            events.add(new GameEvent.LogAppended("전투 노드를 선택했다. START_COMBAT 명령 대기 중."));
        } else {
            state.nodeState(NodeState.NON_COMBAT);
            if (choice.phase() == RunState.NodePhase.JUDGEMENT) {
                state.runState().resolveCurrentNode(
                        "reward",
                        "보상 획득",
                        choice.name() + " 결과 확인",
                        "판정을 통과해 200G와 열쇠 1개를 확보했다.",
                        200,
                        1,
                        0
                );
            } else {
                state.runState().resolveCurrentNode(
                        "reward",
                        "탐색 완료",
                        choice.name() + " 결과 확인",
                        "이벤트를 정리하고 120G와 상자 1개를 획득했다.",
                        120,
                        0,
                        1
                );
            }
            events.add(new GameEvent.LogAppended("노드 결과가 recentResults에 기록되었다."));
        }
        return events;
    }
}
