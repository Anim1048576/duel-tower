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

public record ResolveJudgementCommand(
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
        if (choiceId == null || choiceId.isBlank()) {
            errors.add("choiceId is required");
            return errors;
        }
        if (state.combat() != null || state.nodeState() == NodeState.COMBAT) {
            errors.add("cannot resolve judgement during combat");
            return errors;
        }
        if (!RunCommandSupport.isJudgementNodePending(state)) {
            errors.add("judgement is not pending");
            return errors;
        }
        PendingDecision.JudgementChoice decision = RunCommandSupport.pendingJudgementChoice(state, playerId);
        if (decision == null) {
            errors.add("no pending judgement decision");
            return errors;
        }
        if (!decision.choiceIds().contains(choiceId.trim())) {
            errors.add("invalid judgement choice");
        }
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        String normalizedChoice = choiceId.trim();
        state.player(playerId).pendingDecision(null);

        boolean success = "SUCCESS".equalsIgnoreCase(normalizedChoice);
        if (success) {
            state.runState().resolveCurrentNode(
                    "reward",
                    "판정 성공",
                    "위험 구역 돌파 성공",
                    "판정을 통과해 200G와 열쇠 1개를 확보했다.",
                    200,
                    1,
                    0
            );
        } else {
            state.runState().resolveCurrentNode(
                    "reward",
                    "판정 실패",
                    "위험 구역 돌파 실패",
                    "시간을 소모했지만 80G를 건졌다.",
                    80,
                    0,
                    0
            );
        }

        return List.of(new GameEvent.LogAppended(playerId.value() + " 판정 처리: " + normalizedChoice));
    }
}
