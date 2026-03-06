package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.CombatPhase;
import com.example.dueltower.engine.model.CombatState;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.TargetRef;

import java.util.List;

/**
 * "현재 턴 종료" 후 "다음 턴 시작"까지의 흐름을 한 곳에 모아둔다.
 */
public final class TurnFlow {
    private TurnFlow() {}

    public static final TurnAdvancePolicy DEFAULT_POLICY = new DefaultTurnAdvancePolicy();

    /**
     * 현재 actor의 턴을 종료하고, 다음 턴 시작 상태로 만든다.
     */
    public static void endCurrentAndAdvance(GameState state, EngineContext ctx, List<GameEvent> out) {
        endCurrentAndAdvance(state, ctx, out, DEFAULT_POLICY);
    }

    public static void endCurrentAndAdvance(
            GameState state,
            EngineContext ctx,
            List<GameEvent> out,
            TurnAdvancePolicy policy
    ) {
        CombatState cs = state.combat();
        if (cs == null) throw new IllegalStateException("combat not started");

        TargetRef current = cs.currentTurnActor();
        out.add(new GameEvent.LogAppended(CombatState.actorKey(current) + " ends turn"));

        cs.phase(CombatPhase.TURN_END);
        TurnPhases.turnEnd(state, ctx, current, out, "TURN_END");

        advanceOne(state, cs);
        normalizeToPlayableTurn(state, ctx, out, policy);
    }

    /**
     * 전투 시작 직후 현재 턴 actor의 TURN_START 처리까지 수행한다.
     */
    public static void initializeFirstTurn(GameState state, EngineContext ctx, List<GameEvent> out) {
        initializeFirstTurn(state, ctx, out, DEFAULT_POLICY);
    }

    public static void initializeFirstTurn(
            GameState state,
            EngineContext ctx,
            List<GameEvent> out,
            TurnAdvancePolicy policy
    ) {
        CombatState cs = state.combat();
        if (cs == null) throw new IllegalStateException("combat not started");

        normalizeToPlayableTurn(state, ctx, out, policy);
    }

    private static void normalizeToPlayableTurn(
            GameState state,
            EngineContext ctx,
            List<GameEvent> out,
            TurnAdvancePolicy policy
    ) {
        CombatState cs = state.combat();
        if (cs == null) throw new IllegalStateException("combat not started");

        int guard = Math.max(1, cs.turnOrder().size()) * 2;
        while (guard-- > 0) {
            TargetRef actor = cs.currentTurnActor();

            cs.phase(CombatPhase.TURN_START);
            TurnPhases.turnStart(state, ctx, actor, out, "TURN_START");

            if (!policy.autoEndAfterTurnStart(state, actor)) {
                cs.phase(CombatPhase.MAIN);
                return;
            }

            out.add(new GameEvent.LogAppended(policy.autoEndMessage(state, actor)));

            cs.phase(CombatPhase.TURN_END);
            TurnPhases.turnEnd(state, ctx, actor, out, "TURN_END");
            advanceOne(state, cs);
        }

        // fail-safe: guard가 소진되어도 턴은 MAIN으로 둔다.
        cs.phase(CombatPhase.MAIN);
    }

    /**
     * 턴 인덱스/라운드 증가 + 라운드 경계 처리(EX 쿨다운 만료 등)
     */
    private static void advanceOne(GameState state, CombatState cs) {
        int nextIndex = cs.currentTurnIndex() + 1;
        int nextRound = cs.round();

        if (nextIndex >= cs.turnOrder().size()) {
            nextIndex = 0;
            nextRound = cs.round() + 1;
            cs.round(nextRound);

            // 라운드가 넘어가는 시점에 EX 쿨다운 만료 정리
            for (PlayerState ps : state.players().values()) {
                if (ps.exCooldownUntilRound() > 0 && nextRound > ps.exCooldownUntilRound()) {
                    ps.exCooldownUntilRound(0);
                }
            }
            state.enemies().values().forEach(es -> {
                if (es.exCooldownUntilRound() > 0 && nextRound > es.exCooldownUntilRound()) {
                    es.exCooldownUntilRound(0);
                }
            });
        }

        cs.currentTurnIndex(nextIndex);
    }

    public interface TurnAdvancePolicy {
        boolean autoEndAfterTurnStart(GameState state, TargetRef actor);

        String autoEndMessage(GameState state, TargetRef actor);
    }

    private static final class DefaultTurnAdvancePolicy implements TurnAdvancePolicy {
        @Override
        public boolean autoEndAfterTurnStart(GameState state, TargetRef actor) {
            if (actor instanceof TargetRef.Enemy) return true;
            if (actor instanceof TargetRef.Player p) {
                PlayerState ps = state.player(p.id());
                return CombatStatuses.isBattleIncapacitated(ps);
            }
            return false;
        }

        @Override
        public String autoEndMessage(GameState state, TargetRef actor) {
            if (actor instanceof TargetRef.Player p) {
                PlayerState ps = state.player(p.id());
                if (CombatStatuses.isBattleIncapacitated(ps)) {
                    return CombatState.actorKey(actor) + " turn (auto-end: battle incapacitated)";
                }
            }
            return CombatState.actorKey(actor) + " turn (auto-end: policy)";
        }
    }
}
