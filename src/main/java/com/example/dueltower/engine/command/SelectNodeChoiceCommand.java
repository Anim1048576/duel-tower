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
            if (state.runState().currentNodeForcedSuccessJudgement()) {
                int successGold = ctx.rewardTable().judgement().successGold();
                int successKeys = ctx.rewardTable().judgement().successKeys();
                state.runState().resolveCurrentNode(
                        "reward",
                        "판정 성공",
                        "강제 진행 판정 성공",
                        "강제 진행 판정으로 능력치 선택/주사위/기억 받아들이기 없이 즉시 성공했다. 보상: "
                                + successGold + "G, 열쇠 " + successKeys + "개.",
                        successGold,
                        successKeys,
                        0
                );
                events.add(new GameEvent.LogAppended("판정 노드 진입: 강제 진행 판정이라 즉시 성공 처리했다."));
                return events;
            }
            List<String> abilityChoices = JudgementEngine.judgementAbilityChoices(state.player(playerId));
            if (abilityChoices.isEmpty()) {
                int failureGold = ctx.rewardTable().judgement().failureGold();
                state.runState().resolveCurrentNode(
                        "reward",
                        "판정 불가",
                        "선택 가능한 능력치 없음",
                        "모든 능력치가 최대치(" + JudgementEngine.MAX_ABILITY + ")라 판정을 진행할 수 없습니다. 보상: " + failureGold + "G.",
                        failureGold,
                        0,
                        0
                );
                events.add(new GameEvent.LogAppended("판정 노드 진입: 선택 가능한 능력치가 없어 판정을 즉시 종료했다."));
            } else {
                state.player(playerId).pendingDecision(new PendingDecision.JudgementChoice(
                        "판정에 사용할 능력치를 선택하세요",
                        abilityChoices
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
