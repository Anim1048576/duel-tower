package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.List;

/**
 * "현재 턴 종료" 후 "다음 턴 시작"까지의 흐름을 한 곳에 모아둔다.
 * 주의: 현재는 적(AI) 턴이 구현되지 않았으므로, 적 턴은 자동 스킵(상태 틱만 처리)한다.
 */
public final class TurnFlow {
    private TurnFlow() {}

    /**
     * 현재 actor의 턴을 종료하고, 다음 '플레이어'의 턴을 시작 상태로 만든다.
     */
    public static void endCurrentAndAdvanceToNextPlayer(GameState state, EngineContext ctx, List<GameEvent> out) {
        CombatState cs = state.combat();
        if (cs == null) throw new IllegalStateException("combat not started");

        TargetRef current = cs.currentTurnActor();
        out.add(new GameEvent.LogAppended(CombatState.actorKey(current) + " ends turn"));
        TurnPhases.turnEnd(state, ctx, current, out, "TURN_END");

        advanceOne(state, cs);

        // 적 턴이 현재 엔진에서 직접 입력을 받을 수 없으므로, 연속된 적 턴은 자동으로 넘긴다.
        int guard = Math.max(1, cs.turnOrder().size()) * 2;
        while (guard-- > 0 && cs.currentTurnActor() instanceof TargetRef.Enemy) {
            TargetRef enemy = cs.currentTurnActor();
            out.add(new GameEvent.LogAppended(CombatState.actorKey(enemy) + " turn (auto-skip: enemy AI not implemented)"));
            TurnPhases.turnStart(state, ctx, enemy, out, "TURN_START");
            TurnPhases.turnEnd(state, ctx, enemy, out, "TURN_END");
            advanceOne(state, cs);
        }

        // 이제 플레이어 턴 시작
        TargetRef next = cs.currentTurnActor();
        TurnPhases.turnStart(state, ctx, next, out, "TURN_START");
    }

    /**
     * 전투 시작 직후, 현재 actor가 플레이어가 아니면(적이 선공이면) 적 턴을 자동 스킵해서 플레이어 턴으로 맞춘다.
     */
    public static void normalizeToPlayerAtCombatStart(GameState state, EngineContext ctx, List<GameEvent> out) {
        CombatState cs = state.combat();
        if (cs == null) throw new IllegalStateException("combat not started");

        int guard = Math.max(1, cs.turnOrder().size()) * 2;
        while (guard-- > 0 && cs.currentTurnActor() instanceof TargetRef.Enemy) {
            TargetRef enemy = cs.currentTurnActor();
            out.add(new GameEvent.LogAppended(CombatState.actorKey(enemy) + " opens combat (auto-skip: enemy AI not implemented)"));
            TurnPhases.turnStart(state, ctx, enemy, out, "TURN_START");
            TurnPhases.turnEnd(state, ctx, enemy, out, "TURN_END");
            advanceOne(state, cs);
        }

        TurnPhases.turnStart(state, ctx, cs.currentTurnActor(), out, "TURN_START");
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
        }

        cs.currentTurnIndex(nextIndex);
    }
}
