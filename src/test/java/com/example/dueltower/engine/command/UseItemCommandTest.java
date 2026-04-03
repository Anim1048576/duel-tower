package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UseItemCommandTest {

    @Test
    void useItemConsumesInventoryAndAppliesHealToSelfByDefault() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 123L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        player.hp(5);
        state.players().put(playerId, player);
        seedCombatMainTurn(state, playerId);

        int beforeCount = findItem(state, "I-1").count();

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-1",
                2,
                TargetSelection.empty()
        );

        EngineResult result = new GameEngine().process(state, new EngineContext(Map.of(), Map.of()), command);

        assertTrue(result.accepted());
        assertEquals(beforeCount - 2, findItem(state, "I-1").count());
        assertTrue(player.hp() > 5);
    }

    @Test
    void useItemRejectsWhenCountIsInsufficient() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 124L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        state.players().put(playerId, new PlayerState(playerId));
        seedCombatMainTurn(state, playerId);

        UseItemCommand command = new UseItemCommand(
                UUID.randomUUID(),
                state.version(),
                playerId,
                "I-2",
                9,
                TargetSelection.empty()
        );

        EngineResult result = new GameEngine().process(state, new EngineContext(Map.of(), Map.of()), command);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("not enough item count"));
    }

    private static void seedCombatMainTurn(GameState state, Ids.PlayerId playerId) {
        CombatState combat = new CombatState();
        combat.phase(CombatPhase.MAIN);
        combat.turnOrder().add(TargetRef.ofPlayer(playerId));
        combat.currentTurnIndex(0);
        state.combat(combat);
        state.nodeState(NodeState.COMBAT);
    }

    private static RunState.InventoryItem findItem(GameState state, String itemId) {
        return state.runState().inventory().items().stream()
                .filter(item -> item.id().equals(itemId))
                .findFirst()
                .orElseThrow();
    }
}
