package com.example.dueltower.engine.command;

import com.example.dueltower.config.RewardTableConfig;
import com.example.dueltower.config.GameRules;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenChestCommandTest {

    @Test
    void openChestUsesConfiguredRewards() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 901L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));
        state.nodeState(NodeState.NON_COMBAT);
        state.runState().inventory().chests(2);
        int beforeGold = state.runState().inventory().gold();
        int beforeItemCount = InventoryCommandSupport.findItemEntry(state, "I-4") == null
                ? 0
                : InventoryCommandSupport.findItemEntry(state, "I-4").count();

        RewardTableConfig rewardConfig = new RewardTableConfig(
                new RewardTableConfig.ChestReward(220, "I-4", 2),
                RewardTableConfig.defaults().judgement(),
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
                new OpenChestCommand(UUID.randomUUID(), state.version(), playerId, 2));

        assertTrue(result.accepted());
        assertEquals(beforeGold + 440, state.runState().inventory().gold());
        assertEquals(0, state.runState().inventory().chests());
        assertEquals(beforeItemCount + 4, InventoryCommandSupport.findItemEntry(state, "I-4").count());
        assertEquals("상자에서 440G와 I-4 4개를 획득했다.", state.runState().recentResults().get(0).detail());
        assertTrue(state.runState().resultPending());
    }
}
