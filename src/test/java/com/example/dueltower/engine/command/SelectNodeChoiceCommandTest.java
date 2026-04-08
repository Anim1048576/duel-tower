package com.example.dueltower.engine.command;

import com.example.dueltower.config.RewardTableConfig;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.config.RunConfig;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.ItemRef;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.RunState;
import com.example.dueltower.engine.core.combat.VictoryOps;
import com.example.dueltower.engine.model.CombatState;
import com.example.dueltower.engine.event.GameEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SelectNodeChoiceCommandTest {

    @Test
    @DisplayName("유효한 선택은 run state를 갱신하고 accepted를 반환한다")
    void validChoiceUpdatesRunStateAndReturnsAccepted() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 123L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));

        String choiceId = state.runState().availableChoices().stream()
                .filter(choice -> !choice.disabled())
                .findFirst()
                .orElseThrow()
                .id();

        SelectNodeChoiceCommand command = new SelectNodeChoiceCommand(UUID.randomUUID(), state.version(), playerId, choiceId);

        GameEngine engine = new GameEngine();
        EngineResult result = engine.process(state, new EngineContext(Map.of(), Map.of()), command);

        assertTrue(result.accepted());
        assertNotNull(state.runState().currentNode());
        assertEquals(choiceId, state.runState().currentNode().id());
        assertEquals(1, state.runState().floor());
        assertTrue(state.nodeState() == NodeState.NON_COMBAT || state.nodeState() == NodeState.COMBAT);
    }

    @Test
    @DisplayName("유효하지 않은 선택은 거부된다")
    void invalidChoiceIsRejected() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 456L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));

        SelectNodeChoiceCommand command = new SelectNodeChoiceCommand(UUID.randomUUID(), state.version(), playerId, "NOT-EXISTS");

        GameEngine engine = new GameEngine();
        EngineResult result = engine.process(state, new EngineContext(Map.of(), Map.of()), command);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("choice not found"));
        assertNull(state.runState().currentNode());
    }

    @Test
    @DisplayName("판정 선택지는 20인 능력치를 제외한다")
    void judgementChoicesExcludeAbilitiesAtTwenty() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 456L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.body(20);
        player.skill(19);
        player.sense(3);
        player.will(10);
        state.players().put(playerId, player);

        String choiceId = state.runState().availableChoices().stream()
                .filter(choice -> choice.phase() == com.example.dueltower.engine.model.RunState.NodePhase.JUDGEMENT)
                .findFirst()
                .orElseThrow()
                .id();

        EngineResult result = new GameEngine().process(
                state,
                new EngineContext(Map.of(), Map.of()),
                new SelectNodeChoiceCommand(UUID.randomUUID(), state.version(), playerId, choiceId)
        );

        assertTrue(result.accepted());
        assertTrue(player.pendingDecision() instanceof com.example.dueltower.engine.model.PendingDecision.JudgementChoice);
        com.example.dueltower.engine.model.PendingDecision.JudgementChoice decision =
                (com.example.dueltower.engine.model.PendingDecision.JudgementChoice) player.pendingDecision();
        assertFalse(decision.choiceIds().contains("BODY"));
        assertEquals(3, decision.choiceIds().size());
        assertTrue(decision.choiceIds().contains("SKILL"));
        assertTrue(decision.choiceIds().contains("SENSE"));
        assertTrue(decision.choiceIds().contains("WILL"));
    }

    @Test
    @DisplayName("사용 가능한 능력치 선택지가 없으면 판정 노드는 자동으로 해결된다")
    void judgementNodeAutoResolvesWhenNoAvailableAbilityChoices() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 789L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.body(20);
        player.skill(20);
        player.sense(20);
        player.will(20);
        state.players().put(playerId, player);

        int beforeGold = state.runState().inventory().gold();
        int failureGold = com.example.dueltower.config.RewardTableConfig.defaults().judgement().failureGold();

        String choiceId = state.runState().availableChoices().stream()
                .filter(choice -> choice.phase() == com.example.dueltower.engine.model.RunState.NodePhase.JUDGEMENT)
                .findFirst()
                .orElseThrow()
                .id();

        EngineResult result = new GameEngine().process(
                state,
                new EngineContext(Map.of(), Map.of()),
                new SelectNodeChoiceCommand(UUID.randomUUID(), state.version(), playerId, choiceId)
        );

        assertTrue(result.accepted());
        assertNull(player.pendingDecision());
        assertTrue(state.runState().resultPending());
        assertEquals(beforeGold + failureGold, state.runState().inventory().gold());
        assertEquals("판정 불가", state.runState().recentResults().get(0).title());
    }

    @Test
    @DisplayName("강제 판정 노드는 pending decision 없이 즉시 해결된다")
    void forcedJudgementNodeResolvesImmediatelyWithoutPendingDecision() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 101L, forcedJudgementRunConfig());
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.body(20);
        player.skill(20);
        player.sense(20);
        player.will(20);
        state.players().put(playerId, player);

        int beforeGold = state.runState().inventory().gold();
        int beforeKeys = state.runState().inventory().keys();
        int successGold = RewardTableConfig.defaults().judgement().successGold();
        int successKeys = RewardTableConfig.defaults().judgement().successKeys();
        String choiceId = state.runState().availableChoices().get(0).id();

        EngineResult result = new GameEngine().process(
                state,
                new EngineContext(Map.of(), Map.of()),
                new SelectNodeChoiceCommand(UUID.randomUUID(), state.version(), playerId, choiceId)
        );

        assertTrue(result.accepted());
        assertNull(player.pendingDecision());
        assertTrue(state.runState().resultPending());
        assertEquals(beforeGold + successGold, state.runState().inventory().gold());
        assertEquals(beforeKeys + successKeys, state.runState().inventory().keys());
        assertEquals("판정 성공", state.runState().recentResults().get(0).title());
        assertTrue(state.runState().recentResults().get(0).detail().contains("강제 진행 판정"));
        assertTrue(result.events().stream()
                .filter(event -> event instanceof com.example.dueltower.engine.event.GameEvent.LogAppended)
                .map(event -> ((com.example.dueltower.engine.event.GameEvent.LogAppended) event).line())
                .anyMatch(message -> message.contains("강제 진행 판정")));
    }

    @Test
    @DisplayName("강제 판정 노드는 memory acceptance 단계를 만들지 않는다")
    void forcedJudgementNodeDoesNotCreateMemoryAcceptanceStep() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 102L, forcedJudgementRunConfig());
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));

        String choiceId = state.runState().availableChoices().get(0).id();

        EngineResult result = new GameEngine().process(
                state,
                new EngineContext(Map.of(), Map.of()),
                new SelectNodeChoiceCommand(UUID.randomUUID(), state.version(), playerId, choiceId)
        );

        assertTrue(result.accepted());
        assertNull(state.player(playerId).pendingDecision());
        assertTrue(state.runState().resultPending());
    }

    @Test
    @DisplayName("전투 노드는 여전히 전투 시작 대기 흐름으로 전이한다")
    void combatNodeStillTransitionsToCombatStartWaitingFlow() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 201L, nodeTypeRunConfig("전투", null, List.of()));
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));

        EngineResult result = new GameEngine().process(
                state,
                new EngineContext(Map.of(), Map.of()),
                new SelectNodeChoiceCommand(UUID.randomUUID(), state.version(), playerId, state.runState().availableChoices().get(0).id())
        );

        assertTrue(result.accepted());
        assertEquals(NodeState.COMBAT, state.nodeState());
        assertFalse(state.runState().resultPending());
        assertTrue(result.events().stream()
                .filter(event -> event instanceof GameEvent.LogAppended)
                .map(event -> ((GameEvent.LogAppended) event).line())
                .anyMatch(message -> message.contains("START_COMBAT")));
    }

    @Test
    @DisplayName("보스 노드는 결과와 로그에서 일반 전투와 구분된다")
    void bossNodeIsSeparatedFromNormalCombatInResultAndLogs() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 202L, nodeTypeRunConfig("보스", null, List.of()));
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));

        EngineResult selectResult = new GameEngine().process(
                state,
                new EngineContext(Map.of(), Map.of()),
                new SelectNodeChoiceCommand(UUID.randomUUID(), state.version(), playerId, state.runState().availableChoices().get(0).id())
        );
        assertTrue(selectResult.accepted());
        assertEquals(NodeState.COMBAT, state.nodeState());
        assertTrue(selectResult.events().stream()
                .filter(event -> event instanceof GameEvent.LogAppended)
                .map(event -> ((GameEvent.LogAppended) event).line())
                .anyMatch(message -> message.contains("보스 노드")));

        state.combat(new CombatState());
        List<GameEvent> postEvents = new java.util.ArrayList<>();
        VictoryOps.postHandleCheck(state, new EngineContext(Map.of(), Map.of()), postEvents);

        assertTrue(state.runState().resultPending());
        assertTrue(state.runState().currentFloorCleared());
        assertTrue(state.runState().canAdvanceToNextFloor());
        assertEquals("보스 전투 결과", state.runState().recentResults().get(0).title());
        assertTrue(state.runState().recentResults().get(0).detail().contains("안전 구획"));
        assertTrue(postEvents.stream()
                .filter(event -> event instanceof GameEvent.LogAppended)
                .map(event -> ((GameEvent.LogAppended) event).line())
                .anyMatch(message -> message.contains("boss combat ends")));
        assertTrue(postEvents.stream()
                .filter(event -> event instanceof GameEvent.LogAppended)
                .map(event -> ((GameEvent.LogAppended) event).line())
                .anyMatch(message -> message.contains("안전 구획")));

        state.runState().completeResultAndPrepareNext(202L);
        assertEquals(2, state.runState().floor());
        assertFalse(state.runState().currentFloorCleared());
        assertFalse(state.runState().canAdvanceToNextFloor());
    }

    @Test
    @DisplayName("일반 전투 승리는 층 클리어로 처리되지 않는다")
    void normalCombatWinDoesNotClearFloor() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 302L, nodeTypeRunConfig("전투", null, List.of()));

        state.runState().beginNode(state.runState().availableChoices().get(0));
        state.combat(new CombatState());
        List<GameEvent> postEvents = new java.util.ArrayList<>();
        VictoryOps.postHandleCheck(state, new EngineContext(Map.of(), Map.of()), postEvents);

        assertTrue(state.runState().resultPending());
        assertFalse(state.runState().currentFloorCleared());
        assertFalse(state.runState().canAdvanceToNextFloor());
        state.runState().completeResultAndPrepareNext(302L);
        assertEquals(1, state.runState().floor());
    }

    @Test
    @DisplayName("보스 전투 패배는 층 클리어로 처리되지 않는다")
    void bossCombatLoseDoesNotClearFloor() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 303L, nodeTypeRunConfig("보스", null, List.of()));
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.hp(0);
        state.players().put(playerId, player);
        state.runState().beginNode(state.runState().availableChoices().get(0));
        state.combat(new CombatState());

        List<GameEvent> postEvents = new java.util.ArrayList<>();
        VictoryOps.postHandleCheck(state, new EngineContext(Map.of(), Map.of()), postEvents);

        assertTrue(state.runState().resultPending());
        assertEquals("보스 전투 패배", state.runState().recentResults().get(0).summary());
        assertFalse(state.runState().currentFloorCleared());
        assertFalse(state.runState().canAdvanceToNextFloor());
        state.runState().completeResultAndPrepareNext(303L);
        assertEquals(1, state.runState().floor());
    }

    @Test
    @DisplayName("시설 노드는 combat pending 없이 즉시 해결된다")
    void facilityNodeResolvesImmediatelyWithoutCombatPending() {
        RunConfig.NodeEffect effect = new RunConfig.NodeEffect(55, 0, 0, 4, "시설 보상", "시설에서 휴식하고 55G를 얻는다.");
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 203L, nodeTypeRunConfig("시설", effect, List.of()));
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.hp(10);
        state.players().put(playerId, player);
        int beforeGold = state.runState().inventory().gold();

        EngineResult result = new GameEngine().process(
                state,
                new EngineContext(Map.of(), Map.of()),
                new SelectNodeChoiceCommand(UUID.randomUUID(), state.version(), playerId, state.runState().availableChoices().get(0).id())
        );

        assertTrue(result.accepted());
        assertEquals(NodeState.NON_COMBAT, state.nodeState());
        assertTrue(state.runState().resultPending());
        assertEquals(beforeGold + 55, state.runState().inventory().gold());
        assertEquals(14, player.hp());
        assertEquals("시설 이용", state.runState().recentResults().get(0).title());
    }

    @Test
    @DisplayName("저주 노드는 일반 노드와 다른 패널티를 적용한다")
    void curseNodeAppliesPenaltyDifferentFromNormalNode() {
        RunConfig.NodeEffect effect = new RunConfig.NodeEffect(-70, 0, 0, -3, "저주 패널티", "저주로 70G를 잃고 체력 3 감소.");
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 204L, nodeTypeRunConfig("저주", effect, List.of()));
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.hp(20);
        state.players().put(playerId, player);
        int beforeGold = state.runState().inventory().gold();

        EngineResult result = new GameEngine().process(
                state,
                new EngineContext(Map.of(), Map.of()),
                new SelectNodeChoiceCommand(UUID.randomUUID(), state.version(), playerId, state.runState().availableChoices().get(0).id())
        );

        assertTrue(result.accepted());
        assertTrue(state.runState().resultPending());
        assertEquals(beforeGold - 70, state.runState().inventory().gold());
        assertEquals(17, player.hp());
        assertEquals("저주 노출", state.runState().recentResults().get(0).title());
    }

    @Test
    @DisplayName("미스터리 노드는 설정된 시설 결과를 결정적으로 선택한다")
    void mysteryNodeChoosesConfiguredFacilityOutcomeDeterministically() {
        RunConfig.NodeEffect effect = new RunConfig.NodeEffect(30, 0, 0, 2, "???-시설", "??? 결과로 시설 보상.");
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 205L,
                nodeTypeRunConfig("???", effect, List.of("FACILITY")));
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.hp(10);
        state.players().put(playerId, player);
        int beforeGold = state.runState().inventory().gold();

        EngineResult result = new GameEngine().process(
                state,
                new EngineContext(Map.of(), Map.of()),
                new SelectNodeChoiceCommand(UUID.randomUUID(), state.version(), playerId, state.runState().availableChoices().get(0).id())
        );

        assertTrue(result.accepted());
        assertEquals(NodeState.NON_COMBAT, state.nodeState());
        assertTrue(state.runState().resultPending());
        assertEquals(beforeGold + 30, state.runState().inventory().gold());
        assertEquals(12, player.hp());
        assertTrue(result.events().stream()
                .filter(event -> event instanceof GameEvent.LogAppended)
                .map(event -> ((GameEvent.LogAppended) event).line())
                .anyMatch(message -> message.contains("??? 노드 결과: FACILITY")));
    }

    private static RunConfig forcedJudgementRunConfig() {
        return new RunConfig(
                0,
                0,
                100,
                List.of(),
                List.of(new RunConfig.RunNodeDefinition(
                        "FORCED-JUDGE-1",
                        "강제 판정",
                        "판정",
                        "무조건 진행",
                        RunState.NodePhase.JUDGEMENT,
                        RunState.Danger.LOW,
                        false,
                        null,
                        true
                )),
                List.of(new RunState.ShopOffer("TEST-OFFER", new ItemRef("I-1"), 10, 1, false))
        );
    }

    private static RunConfig nodeTypeRunConfig(String typeLabel, RunConfig.NodeEffect effect, List<String> mysteryOutcomes) {
        return RunConfig.fromRaw(new RunConfig.RunConfigRaw(
                0,
                0,
                100,
                List.of(),
                List.of(new RunConfig.RunNodeDefinitionRaw(
                        "NODE-1",
                        typeLabel + " 테스트",
                        typeLabel,
                        typeLabel + " 규칙",
                        "전투".equals(typeLabel) || "보스".equals(typeLabel) ? RunState.NodePhase.COMBAT : RunState.NodePhase.EVENT,
                        RunState.Danger.MID,
                        false,
                        null,
                        false,
                        null,
                        effect == null ? null : new RunConfig.NodeEffectRaw(
                                effect.goldDelta(),
                                effect.keyDelta(),
                                effect.chestDelta(),
                                effect.hpDelta(),
                                effect.summary(),
                                effect.detail()
                        ),
                        mysteryOutcomes
                )),
                List.of(new RunConfig.ShopOfferRaw("TEST-OFFER", "I-1", 10, 1, false))
        ));
    }
}
