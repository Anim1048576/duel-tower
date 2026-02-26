package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.core.status.StatusPhases;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.List;

/**
 * 턴 시작/턴 종료에서 공통으로 실행할 처리들을 한 군데로 모아둔다.
 */
public final class TurnPhases {
    private TurnPhases() {}

    /**
     * 턴 시작 처리
     * - 상태 효과(turnStart)
     * - (플레이어만) 패 교환 플래그 초기화
     * - (플레이어만) 드로우: 손패 < 4면 2장, 아니면 1장
     * - (플레이어만) 손패 제한(6) 초과 시 discard-to-limit 결정 생성
     */
    public static void turnStart(GameState state, EngineContext ctx, TargetRef actor, List<GameEvent> out, String source) {
        StatusPhases.turnStart(state, ctx, actor, out, source);

        if (actor instanceof TargetRef.Player p) {
            PlayerState ps = state.player(p.id());
            if (ps == null) throw new IllegalStateException("missing player: " + p.id().value());

            ps.swappedThisTurn(false);

            int draw = (ps.hand().size() < 4) ? 2 : 1;
            ZoneOps.drawWithRefill(state, ctx, ps, draw, out);

            ensureHandLimitOrSetPending(ps, out);
            out.add(new GameEvent.LogAppended(ps.playerId().value() + " draws " + draw + " (turn start)"));
        }
    }

    /**
     * 턴 종료 처리
     * - 상태 효과(turnEnd)
     * - (플레이어만) 행동력(AP) 회복(기본: max로 리필)
     *   * 집념 등 "턴 종료 시 회복 AP에서 차감" 같은 규칙은 추후 여기서 처리하면 됨.
     */
    public static void turnEnd(GameState state, EngineContext ctx, TargetRef actor, List<GameEvent> out, String source) {
        StatusPhases.turnEnd(state, ctx, actor, out, source);

        if (actor instanceof TargetRef.Player p) {
            PlayerState ps = state.player(p.id());
            if (ps == null) throw new IllegalStateException("missing player: " + p.id().value());
            ps.ap(ps.maxAp());
        }
    }

    private static void ensureHandLimitOrSetPending(PlayerState ps, List<GameEvent> out) {
        if (ps.hand().size() > ps.handLimit() && ps.pendingDecision() == null) {
            ps.pendingDecision(new PendingDecision.DiscardToHandLimit("hand limit exceeded", ps.handLimit()));
            out.add(new GameEvent.PendingDecisionSet(ps.playerId().value(), "DISCARD_TO_HAND_LIMIT", "hand limit exceeded"));
        }
    }
}
