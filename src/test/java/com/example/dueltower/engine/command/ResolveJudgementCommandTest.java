package com.example.dueltower.engine.command;

import com.example.dueltower.config.RewardTableConfig;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResolveJudgementCommandTest {

    @Test
    void resolveJudgementUsesConfiguredSuccessReward() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 1001L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);
        state.nodeState(NodeState.NON_COMBAT);
        int beforeGold = state.runState().inventory().gold();
        int beforeKeys = state.runState().inventory().keys();

        String judgementChoiceId = state.runState().availableChoices().stream()
                .filter(choice -> choice.phase() == RunState.NodePhase.JUDGEMENT)
                .findFirst()
                .orElseThrow()
                .id();
        state.runState().beginNode(state.runState().findChoice(judgementChoiceId));
        player.pendingDecision(new PendingDecision.JudgementChoice("판정", java.util.List.of("SUCCESS", "FAIL")));

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
                        null,
                        rewardConfig
                ),
                new ResolveJudgementCommand(UUID.randomUUID(), state.version(), playerId, "SUCCESS"));

        assertTrue(result.accepted());
        assertEquals(beforeGold + 310, state.runState().inventory().gold());
        assertEquals(beforeKeys + 2, state.runState().inventory().keys());
        assertEquals("판정을 통과해 310G와 열쇠 2개를 확보했다.", state.runState().recentResults().get(0).detail());
        assertNull(player.pendingDecision());
    }
}
