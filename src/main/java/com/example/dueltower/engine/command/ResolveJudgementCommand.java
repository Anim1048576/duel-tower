package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.PendingDecision;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.RunState;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ResolveJudgementCommand(
        UUID commandId,
        long expectedVersion,
        PlayerId playerId,
        String choiceId,
        JudgementEngine judgementEngine
) implements GameCommand {
    public ResolveJudgementCommand {
        judgementEngine = Objects.requireNonNull(judgementEngine, "judgementEngine");
    }

    public ResolveJudgementCommand(UUID commandId, long expectedVersion, PlayerId playerId, String choiceId) {
        this(commandId, expectedVersion, playerId, choiceId, new JudgementEngine());
    }

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
            return errors;
        }
        PlayerState player = state.player(playerId);
        String normalizedChoice = choiceId.trim().toUpperCase();
        if ("BODY".equals(normalizedChoice) && player.body() >= JudgementEngine.MAX_ABILITY) {
            errors.add("judgement blocked: ability BODY already maxed");
        } else if ("SKILL".equals(normalizedChoice) && player.skill() >= JudgementEngine.MAX_ABILITY) {
            errors.add("judgement blocked: ability SKILL already maxed");
        } else if ("SENSE".equals(normalizedChoice) && player.sense() >= JudgementEngine.MAX_ABILITY) {
            errors.add("judgement blocked: ability SENSE already maxed");
        } else if ("WILL".equals(normalizedChoice) && player.will() >= JudgementEngine.MAX_ABILITY) {
            errors.add("judgement blocked: ability WILL already maxed");
        }
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        String normalizedChoice = choiceId.trim();
        int successGold = ctx.rewardTable().judgement().successGold();
        int successKeys = ctx.rewardTable().judgement().successKeys();
        int failureGold = ctx.rewardTable().judgement().failureGold();
        JudgementEngine.Result result = judgementEngine.resolve(
                state.player(playerId),
                playerId,
                normalizedChoice,
                state.seed(),
                state.version()
        );
        state.player(playerId).pendingDecision(null);

        if (result.success()) {
            state.runState().resolveCurrentNode(
                    "reward",
                    "판정 성공",
                    "위험 구역 돌파 성공",
                    result.usedAbilityLabel() + " 판정(" + result.usedAbility() + ") D20=" + result.roll() + ", 능력치=" + result.abilityBefore()
                            + "로 성공. 보상: " + successGold + "G, 열쇠 " + successKeys + "개.",
                    successGold,
                    successKeys,
                    0
            );
        } else {
            state.runState().resolveCurrentNode(
                    "reward",
                    "판정 실패",
                    "위험 구역 돌파 실패",
                    result.usedAbilityLabel() + " 판정(" + result.usedAbility() + ") D20=" + result.roll() + ", 능력치=" + result.abilityBefore()
                            + "로 실패. [기억 받아들이기] 발동: 약화 " + result.grantedWeakness()
                            + " 부여, " + result.increasedAbility() + " 능력치 " + result.increasedAbilityValue() + "로 상승. 보상: " + failureGold + "G.",
                    failureGold,
                    0,
                    0
            );
        }

        return List.of(new GameEvent.LogAppended(playerId.value()
                + " 판정 처리: ability=" + result.usedAbility()
                + ", d20=" + result.roll()
                + ", success=" + result.success()
                + ", memoryAccepted=" + result.memoryAccepted()
                + ", weakness=" + result.grantedWeakness()
                + ", increasedAbility=" + result.increasedAbility()
                + ", increasedValue=" + result.increasedAbilityValue()));
    }
}
