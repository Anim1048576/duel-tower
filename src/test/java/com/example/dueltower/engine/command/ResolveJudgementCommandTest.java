package com.example.dueltower.engine.command;

import com.example.dueltower.config.GameRules;
import com.example.dueltower.config.RewardTableConfig;
import com.example.dueltower.content.card.model.OwnedCard;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolveJudgementCommandTest {

    @Test
    void resolveJudgementUsesRuleEngineAndConfiguredReward() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 1001L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        RewardTableConfig rewardConfig = new RewardTableConfig(
                RewardTableConfig.defaults().chest(),
                new RewardTableConfig.JudgementReward(10, 0, 99),
                RewardTableConfig.defaults().sellPrices()
        );
        player.body(20);
        player.skill(20);
        player.sense(20);
        player.will(0);
        player.ownedCards(List.of(new OwnedCard("oc-1", "C001", List.of())));
        player.deckOwnedCardIds(List.of("oc-1"));
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
        JudgementEngine judgementEngine = new JudgementEngine(
                (seed, version, pid, abilityId) -> 20,
                (pool, seed, version, pid, abilityId) -> "WEAKENED_COST_PLUS_ONE",
                (pool, seed, version, pid, abilityId) -> "oc-1"
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
                new ResolveJudgementCommand(UUID.randomUUID(), state.version(), playerId, "WILL", judgementEngine));

        assertTrue(result.accepted());
        assertTrue(state.runState().resultPending());
        assertEquals(beforeGold + 99, state.runState().inventory().gold());
        assertFalse(state.runState().recentResults().get(0).detail().isBlank());
        assertEquals(1, player.will());
        assertTrue(player.ownedCards().get(0).hasModifier("WEAKENED_COST_PLUS_ONE"));
        assertTrue(result.events().stream().anyMatch(ev -> ev instanceof com.example.dueltower.engine.event.GameEvent.LogAppended log
                && log.line().contains("memoryAccepted=true")
                && log.line().contains("targetOwnedCardId=oc-1")));
    }

    @Test
    void resolveJudgementRejectsWhenAbilityAlreadyMaxed() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 1001L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.body(20);
        player.ownedCards(List.of(new OwnedCard("oc-1", "C001", List.of())));
        player.deckOwnedCardIds(List.of("oc-1"));
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
