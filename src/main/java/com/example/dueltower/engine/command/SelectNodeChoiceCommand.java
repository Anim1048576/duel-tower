package com.example.dueltower.engine.command;

import com.example.dueltower.engine.config.RunConfig;
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
import java.util.Random;
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
        RunConfig.RunNodeDefinition nodeDefinition = state.runState().nodeDefinition(choice.id());
        RunConfig.NodeType nodeType = nodeDefinition == null
                ? RunConfig.NodeType.parse(null, choice.typeLabel())
                : nodeDefinition.nodeType();

        List<GameEvent> events = new ArrayList<>();
        events.add(new GameEvent.LogAppended("노드 선택: " + choice.name() + " (" + choice.typeLabel() + ")"));

        if (nodeType == RunConfig.NodeType.BOSS) {
            state.nodeState(NodeState.COMBAT);
            events.add(new GameEvent.LogAppended("보스 노드를 선택했다. START_COMBAT 명령 대기 중."));
            return events;
        }
        if (nodeType == RunConfig.NodeType.COMBAT || choice.phase() == RunState.NodePhase.COMBAT) {
            state.nodeState(NodeState.COMBAT);
            events.add(new GameEvent.LogAppended("전투 노드를 선택했다. START_COMBAT 명령 대기 중."));
            return events;
        }
        if (nodeType == RunConfig.NodeType.FACILITY) {
            state.nodeState(NodeState.NON_COMBAT);
            applyFacilityEffect(state, nodeDefinition, playerId, events);
            return events;
        }
        if (nodeType == RunConfig.NodeType.CURSE) {
            state.nodeState(NodeState.NON_COMBAT);
            applyCurseEffect(state, nodeDefinition, playerId, events);
            return events;
        }
        if (nodeType == RunConfig.NodeType.MYSTERY) {
            state.nodeState(NodeState.NON_COMBAT);
            RunConfig.NodeType resolvedType = pickMysteryOutcome(nodeDefinition, state);
            events.add(new GameEvent.LogAppended("??? 노드 결과: " + resolvedType.name()));
            if (resolvedType == RunConfig.NodeType.COMBAT) {
                state.nodeState(NodeState.COMBAT);
                events.add(new GameEvent.LogAppended("??? 결과로 전투가 시작된다. START_COMBAT 명령 대기 중."));
            } else if (resolvedType == RunConfig.NodeType.FACILITY) {
                applyFacilityEffect(state, nodeDefinition, playerId, events);
            } else {
                applyCurseEffect(state, nodeDefinition, playerId, events);
            }
            return events;
        }

        if (choice.phase() == RunState.NodePhase.JUDGEMENT) {
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

    private static RunConfig.NodeType pickMysteryOutcome(RunConfig.RunNodeDefinition definition, GameState state) {
        List<RunConfig.NodeType> outcomes = definition == null
                ? List.of(RunConfig.NodeType.FACILITY, RunConfig.NodeType.CURSE, RunConfig.NodeType.COMBAT)
                : definition.mysteryOutcomes();
        if (outcomes.isEmpty()) {
            outcomes = List.of(RunConfig.NodeType.FACILITY, RunConfig.NodeType.CURSE, RunConfig.NodeType.COMBAT);
        }
        int nodeHash = definition == null ? 0 : definition.id().hashCode();
        Random random = new Random(state.seed() ^ state.version() ^ outcomes.size() ^ nodeHash);
        return outcomes.get(random.nextInt(outcomes.size()));
    }

    private static void applyFacilityEffect(
            GameState state,
            RunConfig.RunNodeDefinition nodeDefinition,
            PlayerId playerId,
            List<GameEvent> events
    ) {
        RunConfig.NodeEffect effect = nodeDefinition == null || nodeDefinition.effect() == null
                ? new RunConfig.NodeEffect(120, 0, 0, 5, "시설 정비", "시설에서 장비를 정비해 120G를 얻고 체력을 5 회복했다.")
                : nodeDefinition.effect();
        applyNodeEffect(state, playerId, "facility", "시설 이용", effect, events);
    }

    private static void applyCurseEffect(
            GameState state,
            RunConfig.RunNodeDefinition nodeDefinition,
            PlayerId playerId,
            List<GameEvent> events
    ) {
        RunConfig.NodeEffect effect = nodeDefinition == null || nodeDefinition.effect() == null
                ? new RunConfig.NodeEffect(-80, 0, 0, -6, "저주 발현", "저주 기운이 스며들어 80G를 잃고 체력이 6 감소했다.")
                : nodeDefinition.effect();
        applyNodeEffect(state, playerId, "curse", "저주 노출", effect, events);
    }

    private static void applyNodeEffect(
            GameState state,
            PlayerId playerId,
            String resultType,
            String title,
            RunConfig.NodeEffect effect,
            List<GameEvent> events
    ) {
        PlayerState player = state.player(playerId);
        if (player != null && effect.hpDelta() != 0) {
            player.hp(player.hp() + effect.hpDelta());
        }
        String summary = effect.summary() == null || effect.summary().isBlank()
                ? title
                : effect.summary();
        String detail = effect.detail() == null || effect.detail().isBlank()
                ? summary
                : effect.detail();
        state.runState().resolveCurrentNode(
                resultType,
                title,
                summary,
                detail,
                effect.goldDelta(),
                effect.keyDelta(),
                effect.chestDelta()
        );
        events.add(new GameEvent.LogAppended(title + " 처리 완료"));
    }
}
