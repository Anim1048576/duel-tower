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
            events.add(new GameEvent.CombatLogAppended(
                    "combat.lastWordsSkipped",
                    "PLAYER",
                    "[유언] 처리 생략: 발동 가능한 유언 효과가 없습니다.",
                    ps.playerId().value(),
                    ps.playerId().value(),
                    null,
                    null,
                    null,
                    null,
                    List.of(
                            "검사 영역: PLAYER_FIELD, PLAYER_GRAVEYARD",
                            "원인: LAST_WORDS 키워드를 가진 카드 없음"
                    ),
                    java.util.Map.of(
                            "reason", "NO_CANDIDATES",
                            "checkedZones", List.of("PLAYER_FIELD", "PLAYER_GRAVEYARD"),
                            "candidateCount", 0
                    )
            ));
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
            events.add(new GameEvent.CombatLogAppended(
                    "combat.lastWordsSkipped",
                    "PLAYER",
                    "[유언] 처리 생략: 지불 가능한 유언 효과가 없습니다.",
                    ps.playerId().value(),
                    ps.playerId().value(),
                    null,
                    null,
                    null,
                    null,
                    List.of(
                            "검사 후보: " + collectedCandidateIds.size(),
                            "현재 AP: " + ps.ap(),
                            "원인: 유언 비용을 지불할 수 없음"
                    ),
                    java.util.Map.of(
                            "reason", "NO_PAYABLE_CANDIDATES",
                            "candidateIds", collectedCandidateIds.stream().map(id -> id.value().toString()).toList(),
                            "currentAp", ps.ap()
                    )
            ));
            events.add(new GameEvent.LogAppended(
                    ps.playerId().value() + " last words skipped: no payable candidates"
            ));
            return false;
        }

        if (ps.pendingDecision() != null) {
            events.add(new GameEvent.CombatLogAppended(
                    "combat.lastWordsSkipped",
                    "PLAYER",
                    "[유언] 처리 생략: 먼저 처리해야 할 선택지가 있습니다.",
                    ps.playerId().value(),
                    ps.playerId().value(),
                    null,
                    null,
                    null,
                    null,
                    List.of(
                            "검사 후보: " + candidateIds.size(),
                            "원인: pending decision already exists"
                    ),
                    java.util.Map.of(
                            "reason", "PENDING_DECISION_EXISTS",
                            "candidateIds", candidateIds.stream().map(id -> id.value().toString()).toList()
                    )
            ));
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
