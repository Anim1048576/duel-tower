package com.example.dueltower.engine.core;

import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;

import java.util.List;

/**
 * 손패 제한(기본 6) 처리 유틸.
 *
 * 룰 요약
 * - 손패가 제한을 초과하면 즉시 제한까지 버린다(플레이어 선택 필요 → PendingDecision).
 * - 단, '부동' 키워드 카드는 버릴 수 없다.
 * - 손패가 '부동' 카드로만(또는 부동이 제한보다 많아) 초과된 경우 버리지 않는다.
 */
public final class HandLimitOps {
    private HandLimitOps() {}

    /**
     * 부동 카드 때문에 손패를 제한 이하로 만들 수 없는 경우를 고려한 "실제 목표 제한".
     *
     * 예) limit=6, hand=10, immovable=8 → effective=8 (부동이 8장이니 8장까지는 허용)
     */
    public static int effectiveHandLimit(GameState state, EngineContext ctx, PlayerState ps) {
        return Math.max(ps.handLimit(), immovableCountInHand(state, ctx, ps));
    }

    public static int immovableCountInHand(GameState state, EngineContext ctx, PlayerState ps) {
        int count = 0;
        for (CardInstId id : ps.hand()) {
            if (isImmovable(state, ctx, id)) count++;
        }
        return count;
    }

    public static boolean isImmovable(GameState state, EngineContext ctx, CardInstId id) {
        if (id == null) return false;
        CardInstance ci = state.card(id);
        if (ci == null) return false;
        CardDefinition def = ctx.def(ci.defId());
        return def.keywords() != null && def.keywords().contains(Keyword.부동);
    }

    /**
     * 손패가 초과되었으면 즉시 DiscardToHandLimit 결정을 설정한다.
     * (부동만으로 초과된 경우는 설정하지 않음)
     */
    public static void ensureHandLimitOrPending(GameState state, EngineContext ctx, PlayerState ps, List<GameEvent> events, String reason) {
        int limit = effectiveHandLimit(state, ctx, ps);
        if (ps.hand().size() > limit) {
            ps.pendingDecision(new PendingDecision.DiscardToHandLimit(reason, limit));
            events.add(new GameEvent.PendingDecisionSet(ps.playerId().value(), "DISCARD_TO_HAND_LIMIT", reason));
        }
    }
}
