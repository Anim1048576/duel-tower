package com.example.dueltower.engine.command;

import com.example.dueltower.common.util.DiceUtility;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class InitiativeTieResolutionCommandTest {

    @Test
    void startCombatEntersTieDecisionPhaseAndBlocksMainUntilResolved() {
        long seed = findPlayerTieSeed();
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), seed);
        EngineContext ctx = new EngineContext(Map.of(), Map.of());
        GameEngine engine = new GameEngine();

        Ids.PlayerId p1 = new Ids.PlayerId("p1");
        Ids.PlayerId p2 = new Ids.PlayerId("p2");
        state.players().put(p1, new PlayerState(p1));
        state.players().put(p2, new PlayerState(p2));
        state.enemies().put(new Ids.EnemyId("e1"), new EnemyState(new Ids.EnemyId("e1"), 30));

        EngineResult start = engine.process(state, ctx, new StartCombatCommand(UUID.randomUUID(), state.version(), p1));

        assertThat(start.accepted()).isTrue();
        assertThat(state.combat().phase()).isEqualTo(CombatPhase.INITIATIVE_TIE_DECISION);
        assertThat(state.combat().initiativeTieGroups()).hasSize(1);
        assertThat(state.player(p1).pendingDecision()).isInstanceOf(PendingDecision.InitiativeTieOrder.class);
        assertThat(state.player(p2).pendingDecision()).isInstanceOf(PendingDecision.InitiativeTieOrder.class);
    }

    @Test
    void resolveInitiativeTieCommandRebuildsOrderAndEntersMainAfterAllGroupsResolved() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 1L);
        EngineContext ctx = new EngineContext(Map.of(), Map.of());

        Ids.PlayerId p1 = new Ids.PlayerId("p1");
        Ids.PlayerId p2 = new Ids.PlayerId("p2");
        PlayerState ps1 = new PlayerState(p1);
        PlayerState ps2 = new PlayerState(p2);
        state.players().put(p1, ps1);
        state.players().put(p2, ps2);
        state.enemies().put(new Ids.EnemyId("e1"), new EnemyState(new Ids.EnemyId("e1"), 20));

        CombatState cs = new CombatState();
        cs.phase(CombatPhase.INITIATIVE_TIE_DECISION);
        cs.turnOrder().add(TargetRef.ofPlayer(p1));
        cs.turnOrder().add(TargetRef.ofPlayer(p2));
        cs.turnOrder().add(TargetRef.ofEnemy(new Ids.EnemyId("e1")));
        cs.initiatives().put("P:p1", 80);
        cs.initiatives().put("P:p2", 80);
        cs.initiatives().put("E:e1", 10);
        cs.initiativeTieGroups().add(List.of("P:p1", "P:p2"));
        state.combat(cs);

        ps1.pendingDecision(new PendingDecision.InitiativeTieOrder("resolve initiative tie order", 0, List.of("P:p1", "P:p2")));
        ps2.pendingDecision(new PendingDecision.InitiativeTieOrder("resolve initiative tie order", 0, List.of("P:p1", "P:p2")));

        GameEngine engine = new GameEngine();
        EngineResult result = engine.process(
                state,
                ctx,
                new ResolveInitiativeTieCommand(UUID.randomUUID(), state.version(), p1, 0, List.of("P:p2", "P:p1"))
        );

        assertThat(result.accepted()).isTrue();
        assertThat(state.combat().initiativeTieGroups()).isEmpty();
        assertThat(state.player(p1).pendingDecision()).isNull();
        assertThat(state.player(p2).pendingDecision()).isNull();
        assertThat(state.combat().phase()).isEqualTo(CombatPhase.MAIN);
        assertThat(state.combat().currentTurnActor()).isEqualTo(TargetRef.ofPlayer(p2));
        assertThat(state.combat().turnOrder().stream().map(CombatState::actorKey).toList())
                .containsExactly("P:p2", "P:p1", "E:e1");
    }

    private static long findPlayerTieSeed() {
        for (long seed = 1; seed < 200_000; seed++) {
            Random rng = new Random(seed);
            int p1 = DiceUtility.rollDice(1, 100, rng);
            int p2 = DiceUtility.rollDice(1, 100, rng);
            if (p1 == p2) return seed;
        }
        throw new IllegalStateException("failed to find seed with player initiative tie");
    }
}
