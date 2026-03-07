package com.example.dueltower.engine.core;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * 카드 검색/선택(PendingDecision.SearchPick) 보조 유틸.
 */
public final class SearchPickOps {
    private SearchPickOps() {}

    /**
     * 현재 덱 순서를 보존해 후보 카드를 결정한다.
     */
    public static List<CardInstId> deckCandidates(GameState state, EngineContext ctx, PlayerState ps, Predicate<CardDefinition> defFilter) {
        List<CardInstId> out = new ArrayList<>();
        if (ps == null) return out;

        Predicate<CardDefinition> filter = (defFilter == null) ? it -> true : defFilter;

        for (CardInstId id : ps.deck()) {
            CardInstance ci = state.card(id);
            if (ci == null || !ps.playerId().equals(ci.ownerId()) || ci.zone() != Zone.DECK) continue;

            CardDefinition def = ctx.def(ci.defId());
            if (!filter.test(def)) continue;
            out.add(id);
        }
        return out;
    }

    /**
     * 덱 후보를 기준으로 SearchPick pending decision을 설정한다.
     *
     * @return pending decision을 실제로 설정했는지 여부
     */
    public static boolean setDeckSearchPickPending(
            GameState state,
            EngineContext ctx,
            PlayerState ps,
            List<GameEvent> events,
            String reason,
            int pickCount,
            Zone destination,
            boolean shuffleAfterPick,
            Predicate<CardDefinition> defFilter
    ) {
        if (ps == null || ps.pendingDecision() != null || pickCount <= 0) return false;

        List<CardInstId> candidateIds = deckCandidates(state, ctx, ps, defFilter);
        if (candidateIds.size() < pickCount) return false;

        ps.pendingDecision(new PendingDecision.SearchPick(
                reason,
                candidateIds,
                pickCount,
                destination,
                shuffleAfterPick,
                UUID.randomUUID()
        ));
        events.add(new GameEvent.PendingDecisionSet(ps.playerId().value(), "SEARCH_PICK", reason));
        return true;
    }
}
