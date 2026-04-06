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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SelectNodeChoiceCommandTest {

    @Test
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
}
