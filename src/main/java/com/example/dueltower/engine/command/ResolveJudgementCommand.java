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
        PendingDecision.JudgementChoice pending = (PendingDecision.JudgementChoice) state.player(playerId).pendingDecision();
        if (pending.usedAbility() == null) {
            return handleInitialSelection(state, ctx, normalizedChoice);
        }
        return handleMemorySelection(state, ctx, pending, normalizedChoice);
    }

    private List<GameEvent> handleInitialSelection(GameState state, EngineContext ctx, String abilityChoice) {
        JudgementEngine.InitialResult initial = judgementEngine.resolveInitial(
                state.player(playerId),
                playerId,
                abilityChoice,
                state.seed(),
                state.version()
        );

        if (initial.initialSuccess()) {
            JudgementEngine.Result result = judgementEngine.finalizeResult(
                    state.player(playerId),
                    playerId,
                    initial,
                    false,
                    state.seed(),
                    state.version()
            );
            state.player(playerId).pendingDecision(null);
            return finalizeNodeWithResult(state, ctx, result);
        }

        if (!initial.memoryAcceptAllowed()) {
            JudgementEngine.Result result = judgementEngine.finalizeResult(
                    state.player(playerId),
                    playerId,
                    initial,
                    false,
                    state.seed(),
                    state.version()
            );
            state.player(playerId).pendingDecision(null);
            return finalizeNodeWithResult(state, ctx, result);
        }

        state.player(playerId).pendingDecision(new PendingDecision.JudgementChoice(
                "판정 실패: [기억 받아들이기] 여부를 선택하세요",
                List.of(JudgementEngine.MEMORY_ACCEPT_CHOICE, JudgementEngine.MEMORY_REJECT_CHOICE),
                initial.usedAbility(),
                initial.roll(),
                initial.abilityBefore(),
                initial.initialSuccess(),
                initial.memoryAcceptAllowed(),
                initial.naturalTwenty()
        ));
        return List.of(new GameEvent.LogAppended(playerId.value()
                + " 판정 1차 처리: ability=" + initial.usedAbility()
                + ", d20=" + initial.roll()
                + ", initialSuccess=" + initial.initialSuccess()
                + ", memoryAcceptAllowed=" + initial.memoryAcceptAllowed()
                + ", naturalTwenty=" + initial.naturalTwenty()
                + ", pending=MEMORY_CHOICE"));
    }

    private List<GameEvent> handleMemorySelection(GameState state, EngineContext ctx, PendingDecision.JudgementChoice pending, String memoryChoice) {
        boolean acceptMemory = JudgementEngine.MEMORY_ACCEPT_CHOICE.equals(memoryChoice);
        JudgementEngine.InitialResult initial = new JudgementEngine.InitialResult(
                pending.usedAbility(),
                abilityLabel(pending.usedAbility()),
                pending.roll(),
                pending.abilityBefore(),
                Boolean.TRUE.equals(pending.initialSuccess()),
                Boolean.TRUE.equals(pending.memoryAcceptAllowed()),
                Boolean.TRUE.equals(pending.naturalTwenty())
        );
        JudgementEngine.Result result = judgementEngine.finalizeResult(
                state.player(playerId),
                playerId,
                initial,
                acceptMemory,
                state.seed(),
                state.version()
        );
        state.player(playerId).pendingDecision(null);
        return finalizeNodeWithResult(state, ctx, result);
    }

    private List<GameEvent> finalizeNodeWithResult(GameState state, EngineContext ctx, JudgementEngine.Result result) {
        int successGold = ctx.rewardTable().judgement().successGold();
        int successKeys = ctx.rewardTable().judgement().successKeys();
        int failureGold = ctx.rewardTable().judgement().failureGold();

        if (result.finalSuccess()) {
            String detail = result.memoryAccepted()
                    ? result.usedAbilityLabel() + " 판정(" + result.usedAbility() + ") D20=" + result.roll() + ", 능력치=" + result.abilityBefore()
                    + "로 최초 실패했지만 [기억 받아들이기]를 선택해 성공으로 전환. 약화 " + result.grantedWeakness()
                    + " 부여, " + result.increasedAbility() + " 능력치 " + result.increasedAbilityValue() + "로 상승. 보상: " + successGold + "G, 열쇠 " + successKeys + "개."
                    : result.usedAbilityLabel() + " 판정(" + result.usedAbility() + ") D20=" + result.roll() + ", 능력치=" + result.abilityBefore()
                    + "로 즉시 성공. 보상: " + successGold + "G, 열쇠 " + successKeys + "개.";
            state.runState().resolveCurrentNode(
                    "reward",
                    "판정 성공",
                    "위험 구역 돌파 성공",
                    detail,
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
                            + "로 최초 실패. 기억 받아들이기 가능 여부=" + result.memoryAcceptAllowed()
                            + ", 최종 선택=" + (result.memoryAccepted() ? "수락" : "거부")
                            + ", " + result.increasedAbility() + " 능력치 " + result.increasedAbilityValue() + "로 상승. 보상: " + failureGold + "G.",
                    failureGold,
                    0,
                    0
            );
        }

        return List.of(new GameEvent.LogAppended(playerId.value()
                + " 판정 최종 처리: ability=" + result.usedAbility()
                + ", d20=" + result.roll()
                + ", initialSuccess=" + result.initialSuccess()
                + ", memoryAcceptAllowed=" + result.memoryAcceptAllowed()
                + ", memoryAccepted=" + result.memoryAccepted()
                + ", finalSuccess=" + result.finalSuccess()
                + ", weakness=" + result.grantedWeakness()
                + ", increasedAbility=" + result.increasedAbility()
                + ", increasedBefore=" + result.abilityBefore()
                + ", increasedAfter=" + result.increasedAbilityValue()
                + ", naturalTwenty=" + (result.roll() == 20)));
    }

    private static String abilityLabel(String abilityId) {
        if ("BODY".equalsIgnoreCase(abilityId)) return "신체";
        if ("SKILL".equalsIgnoreCase(abilityId)) return "기술";
        if ("SENSE".equalsIgnoreCase(abilityId)) return "감각";
        if ("WILL".equalsIgnoreCase(abilityId)) return "의지";
        return abilityId;
    }
}
