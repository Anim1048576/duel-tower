package com.example.dueltower.engine.command;

import com.example.dueltower.config.GameRules;
import com.example.dueltower.config.RewardTableConfig;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.event.GameEvent;
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

import static org.junit.jupiter.api.Assertions.*;

class ResolveJudgementCommandTest {

    @Test
    void abilityBelowOrEqualRollResolvesImmediatelyAsSuccess() {
        Fixture fx = fixtureWithJudgementPending();
        fx.player.body(8);
        fx.player.pendingDecision(new PendingDecision.JudgementChoice("판정", List.of("BODY")));
        JudgementEngine engine = new JudgementEngine(
                (seed, version, pid, abilityId) -> 8,
                (pool, seed, version, pid, abilityId) -> pool.get(0)
        );

        EngineResult result = process(fx, new ResolveJudgementCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, "BODY", engine));

        assertTrue(result.accepted());
        assertNull(fx.player.pendingDecision());
        assertTrue(fx.state.runState().resultPending());
        assertEquals("판정 성공", fx.state.runState().recentResults().get(0).title());
    }

    @Test
    void failedNonNaturalTwentyCreatesMemoryDecisionStage() {
        Fixture fx = fixtureWithJudgementPending();
        fx.player.skill(5);
        fx.player.pendingDecision(new PendingDecision.JudgementChoice("판정", List.of("SKILL")));
        JudgementEngine engine = new JudgementEngine(
                (seed, version, pid, abilityId) -> 9,
                (pool, seed, version, pid, abilityId) -> pool.get(0)
        );

        EngineResult first = process(fx, new ResolveJudgementCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, "SKILL", engine));

        assertTrue(first.accepted());
        assertTrue(fx.player.pendingDecision() instanceof PendingDecision.JudgementChoice pd);
        PendingDecision.JudgementChoice decision = (PendingDecision.JudgementChoice) fx.player.pendingDecision();
        assertEquals(List.of(JudgementEngine.MEMORY_ACCEPT_CHOICE, JudgementEngine.MEMORY_REJECT_CHOICE), decision.choiceIds());
        assertEquals("SKILL", decision.usedAbility());
        assertEquals(9, decision.roll());
        assertTrue(Boolean.TRUE.equals(decision.memoryAcceptAllowed()));
        assertFalse(fx.state.runState().resultPending());
    }

    @Test
    void acceptingMemoryConvertsToFinalSuccess() {
        Fixture fx = fixtureWithJudgementPending();
        fx.player.sense(7);
        fx.player.pendingDecision(new PendingDecision.JudgementChoice("판정", List.of("SENSE")));
        JudgementEngine engine = new JudgementEngine(
                (seed, version, pid, abilityId) -> 12,
                (pool, seed, version, pid, abilityId) -> "WEAKENED_FINAL_HALF"
        );

        EngineResult first = process(fx, new ResolveJudgementCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, "SENSE", engine));
        assertTrue(first.accepted());

        EngineResult second = process(fx, new ResolveJudgementCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, JudgementEngine.MEMORY_ACCEPT_CHOICE, engine));
        assertTrue(second.accepted());

        assertNull(fx.player.pendingDecision());
        assertTrue(fx.state.runState().resultPending());
        assertEquals("판정 성공", fx.state.runState().recentResults().get(0).title());
        assertEquals(10, fx.player.sense());
        assertEquals(1, fx.player.status("judgement.weakness.WEAKENED_FINAL_HALF"));
    }

    @Test
    void rejectingMemoryFinalizesFailureAndIncreasesByOne() {
        Fixture fx = fixtureWithJudgementPending();
        fx.player.will(19);
        fx.player.pendingDecision(new PendingDecision.JudgementChoice("판정", List.of("WILL")));
        JudgementEngine engine = new JudgementEngine(
                (seed, version, pid, abilityId) -> 20,
                (pool, seed, version, pid, abilityId) -> pool.get(0)
        );

        EngineResult first = process(fx, new ResolveJudgementCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, "WILL", engine));
        assertTrue(first.accepted());
        assertNull(fx.player.pendingDecision()); // 자연20은 즉시 실패 확정

        assertTrue(fx.state.runState().resultPending());
        assertEquals("판정 실패", fx.state.runState().recentResults().get(0).title());
        assertEquals(20, fx.player.will());
    }

    @Test
    void pendingDecisionClearsAfterFinalResolutionOnly() {
        Fixture fx = fixtureWithJudgementPending();
        fx.player.body(3);
        fx.player.pendingDecision(new PendingDecision.JudgementChoice("판정", List.of("BODY")));
        JudgementEngine engine = new JudgementEngine(
                (seed, version, pid, abilityId) -> 6,
                (pool, seed, version, pid, abilityId) -> pool.get(0)
        );

        EngineResult first = process(fx, new ResolveJudgementCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, "BODY", engine));
        assertTrue(first.accepted());
        assertNotNull(fx.player.pendingDecision());

        EngineResult second = process(fx, new ResolveJudgementCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, JudgementEngine.MEMORY_REJECT_CHOICE, engine));
        assertTrue(second.accepted());
        assertNull(fx.player.pendingDecision());
    }

    @Test
    void logsContainMemoryAvailabilityAndNaturalTwentyInfo() {
        Fixture fx = fixtureWithJudgementPending();
        fx.player.body(5);
        fx.player.pendingDecision(new PendingDecision.JudgementChoice("판정", List.of("BODY")));
        JudgementEngine engine = new JudgementEngine(
                (seed, version, pid, abilityId) -> 20,
                (pool, seed, version, pid, abilityId) -> pool.get(0)
        );

        EngineResult result = process(fx, new ResolveJudgementCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, "BODY", engine));

        assertTrue(result.accepted());
        assertTrue(result.events().stream().anyMatch(ev -> ev instanceof GameEvent.LogAppended log
                && log.line().contains("memoryAcceptAllowed=false")
                && log.line().contains("naturalTwenty=true")));
    }

    private static EngineResult process(Fixture fx, ResolveJudgementCommand command) {
        return new GameEngine().process(fx.state, fx.ctx, command);
    }

    private static Fixture fixtureWithJudgementPending() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 1001L);
        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);
        state.nodeState(NodeState.NON_COMBAT);

        String judgementChoiceId = state.runState().availableChoices().stream()
                .filter(choice -> choice.phase() == RunState.NodePhase.JUDGEMENT)
                .findFirst()
                .orElseThrow()
                .id();
        state.runState().beginNode(state.runState().findChoice(judgementChoiceId));

        EngineContext ctx = new EngineContext(
                Map.of(), Map.of(),
                Map.of(), Map.of(),
                Map.of(), Map.of(),
                Map.of(), Map.of(),
                Map.of(), Map.of(),
                Map.of(), Map.of(),
                Map.of(),
                GameRules.defaults(),
                RewardTableConfig.defaults()
        );

        return new Fixture(state, playerId, player, ctx);
    }

    private record Fixture(GameState state, Ids.PlayerId playerId, PlayerState player, EngineContext ctx) {}
}
