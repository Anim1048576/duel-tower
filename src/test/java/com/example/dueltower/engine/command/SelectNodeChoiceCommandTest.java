package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.PlayerState;
import org.junit.jupiter.api.Test;

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
}
