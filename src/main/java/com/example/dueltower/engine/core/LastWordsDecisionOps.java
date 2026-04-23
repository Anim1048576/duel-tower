package com.example.dueltower.engine.core;

import com.example.dueltower.content.keyword.kdb.K014_LastWords;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PendingDecision;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;

import java.util.List;

public final class LastWordsDecisionOps {
    private static final String REASON = "resolve last words choice";

    private LastWordsDecisionOps() {}

    public static boolean openPendingIfPossible(EffectContext ec, PlayerState ps, List<GameEvent> events) {
        if (ec == null || ps == null || events == null) {
            return false;
        }

        List<Ids.CardInstId> collectedCandidateIds = ec.lastWordsBatchCollector().candidateIds();
        if (collectedCandidateIds.isEmpty()) {
            events.add(new GameEvent.LogAppended(
                    ps.playerId().value() + " last words skipped: no candidates"
            ));
            return false;
        }

        List<Ids.CardInstId> candidateIds = collectedCandidateIds.stream()
                .filter(candidateId -> {
                    int cost = KeywordOps.keywordValue(ec.state(), ec.ctx(), candidateId, K014_LastWords.ID);
                    return cost > 0 && ps.ap() >= cost;
                })
                .toList();

        if (candidateIds.isEmpty()) {
            events.add(new GameEvent.LogAppended(
                    ps.playerId().value() + " last words skipped: no payable candidates"
            ));
            return false;
        }

        if (ps.pendingDecision() != null) {
            events.add(new GameEvent.LogAppended(
                    ps.playerId().value() + " last words skipped: pending decision already exists"
            ));
            return false;
        }

        ps.pendingDecision(new PendingDecision.LastWordsChoice(
                REASON,
                candidateIds,
                true,
                ec.lastWordsBatchCollector().correlationId()
        ));
        events.add(new GameEvent.PendingDecisionSet(ps.playerId().value(), "LAST_WORDS", REASON));
        events.add(new GameEvent.LogAppended(
                ps.playerId().value() + " opens last words choice " + candidateIds.size()
        ));
        return true;
    }
}
