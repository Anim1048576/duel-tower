package com.example.dueltower.engine.command;

import com.example.dueltower.config.RewardTableConfig;
import com.example.dueltower.config.GameRules;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.PendingDecision;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.RunState;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolveJudgementCommandTest {

    @Test
    void resolveJudgementUsesRuleEngineAndConfiguredReward() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 1001L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.body(20);
        player.skill(20);
        player.sense(20);
        player.will(0);
        state.players().put(playerId, player);
        state.nodeState(NodeState.NON_COMBAT);
        int beforeGold = state.runState().inventory().gold();

        String judgementChoiceId = state.runState().availableChoices().stream()
                .filter(choice -> choice.phase() == RunState.NodePhase.JUDGEMENT)
                .findFirst()
                .orElseThrow()
                .id();
        state.runState().beginNode(state.runState().findChoice(judgementChoiceId));
        player.pendingDecision(new PendingDecision.JudgementChoice("판정", java.util.List.of("WILL")));

        RewardTableConfig rewardConfig = new RewardTableConfig(
                RewardTableConfig.defaults().chest(),
                new RewardTableConfig.JudgementReward(310, 2, 99),
                RewardTableConfig.defaults().sellPrices()
        );

        EngineResult result = new GameEngine().process(state, new EngineContext(
                        Map.of(), Map.of(),
                        Map.of(), Map.of(),
                        Map.of(), Map.of(),
                        Map.of(), Map.of(),
                        Map.of(), Map.of(),
                        Map.of(), Map.of(),
                        Map.of(),
                        GameRules.defaults(),
                        rewardConfig
                ),
                new ResolveJudgementCommand(UUID.randomUUID(), state.version(), playerId, "WILL"));

        assertTrue(result.accepted());
        assertTrue(state.runState().resultPending());
        assertEquals(beforeGold + 99, state.runState().inventory().gold());
        assertFalse(state.runState().recentResults().get(0).detail().isBlank());
        assertEquals(1, player.will());
        assertTrue(result.events().stream().anyMatch(ev -> ev instanceof com.example.dueltower.engine.event.GameEvent.LogAppended log
                && log.line().contains("memoryAccepted=true")));
        assertNull(player.pendingDecision());
    }

    @Test
    void pendingDecisionClearsAfterSuccessfulJudgementAndNoIncreaseSignalInResult() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 2002L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.body(9);
        state.players().put(playerId, player);
        state.nodeState(NodeState.NON_COMBAT);

        String judgementChoiceId = state.runState().availableChoices().stream()
                .filter(choice -> choice.phase() == RunState.NodePhase.JUDGEMENT)
                .findFirst()
                .orElseThrow()
                .id();
        state.runState().beginNode(state.runState().findChoice(judgementChoiceId));
        player.pendingDecision(new PendingDecision.JudgementChoice("판정", java.util.List.of("BODY")));

        JudgementEngine judgementEngine = new JudgementEngine(
                (seed, version, pid, abilityId) -> 1,
                (pool, seed, version, pid, abilityId) -> pool.get(0)
        );

        EngineResult result = new GameEngine().process(state, new EngineContext(
                        Map.of(), Map.of(),
                        Map.of(), Map.of(),
                        Map.of(), Map.of(),
                        Map.of(), Map.of(),
                        Map.of(), Map.of(),
                        Map.of(), Map.of(),
                        Map.of(),
                        GameRules.defaults(),
                        RewardTableConfig.defaults()
                ),
                new ResolveJudgementCommand(UUID.randomUUID(), state.version(), playerId, "BODY", judgementEngine));

        assertTrue(result.accepted());
        assertNull(player.pendingDecision());
        String detail = state.runState().recentResults().get(0).detail();
        assertFalse(detail.contains("상승"));
        assertTrue(result.events().stream().anyMatch(ev -> ev instanceof com.example.dueltower.engine.event.GameEvent.LogAppended log
                && log.line().contains("increasedAbility=null")
                && log.line().contains("increasedValue=null")));
    }

    @Test
    void resolveJudgementRejectsWhenAbilityAlreadyMaxed() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 1001L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.body(20);
        state.players().put(playerId, player);
        state.nodeState(NodeState.NON_COMBAT);

        String judgementChoiceId = state.runState().availableChoices().stream()
                .filter(choice -> choice.phase() == RunState.NodePhase.JUDGEMENT)
                .findFirst()
                .orElseThrow()
                .id();
        state.runState().beginNode(state.runState().findChoice(judgementChoiceId));
        player.pendingDecision(new PendingDecision.JudgementChoice("판정", java.util.List.of("BODY")));

        EngineResult result = new GameEngine().process(state, new EngineContext(
                        Map.of(), Map.of(),
                        Map.of(), Map.of(),
                        Map.of(), Map.of(),
                        Map.of(), Map.of(),
                        Map.of(), Map.of(),
                        Map.of(), Map.of(),
                        Map.of(),
                        GameRules.defaults(),
                        RewardTableConfig.defaults()
                ),
                new ResolveJudgementCommand(UUID.randomUUID(), state.version(), playerId, "BODY"));

        assertFalse(result.accepted());
        assertTrue(result.errors().stream().anyMatch(error -> error.contains("already maxed")));
    }
}
