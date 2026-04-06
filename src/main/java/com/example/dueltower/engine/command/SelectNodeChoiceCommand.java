package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.PendingDecision;
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
        } else if (choice.phase() == RunState.NodePhase.JUDGEMENT) {
            state.nodeState(NodeState.NON_COMBAT);
            List<String> availableAbilities = JudgementEngine.judgementAbilityChoices(state.player(playerId));
            if (availableAbilities.isEmpty()) {
                int failureGold = ctx.rewardTable().judgement().failureGold();
                state.runState().resolveCurrentNode(
                        "reward",
                        "판정 불가",
                        "시도 가능한 능력치 없음",
                        "모든 능력치가 20에 도달해 판정을 시도할 수 없다. " + failureGold + "G를 확보했다.",
                        failureGold,
                        0,
                        0
                );
                events.add(new GameEvent.LogAppended("판정 노드 진입 실패: 선택 가능한 능력치가 없다."));
            } else {
                state.player(playerId).pendingDecision(new PendingDecision.JudgementChoice(
                        "판정에 사용할 능력치를 선택하세요",
                        availableAbilities
                ));
                events.add(new GameEvent.LogAppended("판정 노드 진입: RESOLVE_JUDGEMENT 명령 대기 중."));
            }
        } else {
            state.nodeState(NodeState.NON_COMBAT);
            events.add(new GameEvent.LogAppended("이벤트 노드 진입: BUY_SHOP_ITEM 명령 대기 중."));
        }
        return events;
    }
}
