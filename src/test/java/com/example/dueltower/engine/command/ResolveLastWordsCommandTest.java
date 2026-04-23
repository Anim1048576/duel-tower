package com.example.dueltower.engine.command;

import com.example.dueltower.content.keyword.kdb.K014_LastWords;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.card.LastWordsContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardInstance;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import com.example.dueltower.engine.model.PendingDecision;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.Zone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResolveLastWordsCommandTest {

    @Test
    @DisplayName("skip은 pending을 해제하고 resolveLastWords를 실행하지 않는다")
    void skipClearsPendingWithoutExecutingPayload() {
        Fixture fx = fixture(3, 2);
        ResolveLastWordsCommand command = new ResolveLastWordsCommand(
                UUID.randomUUID(),
                0,
                fx.playerId,
                List.of()
        );

        List<String> errors = command.validate(fx.state, fx.ctx);
        assertTrue(errors.isEmpty());

        List<GameEvent> events = command.handle(fx.state, fx.ctx);

        assertNull(fx.player.pendingDecision());
        assertEquals(0, fx.firstEffect.lastWordsCalls);
        assertEquals(0, fx.secondEffect.lastWordsCalls);
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.PendingDecisionCleared cleared
                && "LAST_WORDS".equals(cleared.type())));
    }

    @Test
    @DisplayName("1개 선택 시 비용을 차감하고 선택한 카드의 resolveLastWords만 실행한다")
    void selectingOneExecutesOnlyChosenPayload() {
        Fixture fx = fixture(5, 2);
        ResolveLastWordsCommand command = new ResolveLastWordsCommand(
                UUID.randomUUID(),
                0,
                fx.playerId,
                List.of(fx.firstCandidate)
        );

        List<String> errors = command.validate(fx.state, fx.ctx);
        assertTrue(errors.isEmpty());

        List<GameEvent> events = command.handle(fx.state, fx.ctx);

        assertEquals(3, fx.player.ap());
        assertNull(fx.player.pendingDecision());
        assertEquals(1, fx.firstEffect.lastWordsCalls);
        assertEquals(0, fx.secondEffect.lastWordsCalls);
        assertEquals(fx.playerId, fx.firstEffect.lastActor);
        assertEquals(fx.firstCandidate, fx.firstEffect.lastSourceCardId);
        assertTrue(fx.firstEffect.sawPendingClearedBeforeResolve);
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.PendingDecisionCleared cleared
                && "LAST_WORDS".equals(cleared.type())));
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.LogAppended log
                && log.line().contains("fixture last words first")));
    }

    @Test
    @DisplayName("validate는 selectedIds 2개 이상을 거부한다")
    void validateRejectsMoreThanOneSelectedId() {
        Fixture fx = fixture(5, 1);
        ResolveLastWordsCommand command = new ResolveLastWordsCommand(
                UUID.randomUUID(),
                0,
                fx.playerId,
                List.of(fx.firstCandidate, fx.secondCandidate)
        );

        List<String> errors = command.validate(fx.state, fx.ctx);
        assertTrue(errors.stream().anyMatch(it -> it.contains("selectedIds must contain 0 or 1 card")));
    }

    @Test
    @DisplayName("validate는 candidateIds에 없는 카드를 거부한다")
    void validateRejectsCardOutsideCandidates() {
        Fixture fx = fixture(5, 1);
        ResolveLastWordsCommand command = new ResolveLastWordsCommand(
                UUID.randomUUID(),
                0,
                fx.playerId,
                List.of(new CardInstId(UUID.fromString("00000000-0000-0000-0000-000000000099")))
        );

        List<String> errors = command.validate(fx.state, fx.ctx);
        assertTrue(errors.stream().anyMatch(it -> it.contains("selected id not in candidates")));
    }

    private static Fixture fixture(int ap, int cost) {
        GameState state = new GameState(new SessionId(UUID.randomUUID()), 777L);
        PlayerId playerId = new PlayerId("P1");
        PlayerState player = new PlayerState(playerId);
        player.will(12);
        player.ap(ap);
        state.players().put(playerId, player);

        TrackingLastWordsEffect firstEffect = new TrackingLastWordsEffect("first");
        TrackingLastWordsEffect secondEffect = new TrackingLastWordsEffect("second");

        CardDefId firstDefId = new CardDefId("LW_FIRST");
        CardDefId secondDefId = new CardDefId("LW_SECOND");

        CardDefinition firstDef = new CardDefinition(firstDefId, "first", CardType.SKILL, 0, Map.of(K014_LastWords.ID, cost), Zone.GRAVE, false, "");
        CardDefinition secondDef = new CardDefinition(secondDefId, "second", CardType.SKILL, 0, Map.of(K014_LastWords.ID, cost), Zone.GRAVE, false, "");

        EngineContext ctx = new EngineContext(
                Map.of(firstDefId, firstDef, secondDefId, secondDef),
                Map.of(firstDefId, firstEffect, secondDefId, secondEffect)
        );

        CardInstId firstCandidate = new CardInstId(UUID.fromString("00000000-0000-0000-0000-000000000011"));
        CardInstId secondCandidate = new CardInstId(UUID.fromString("00000000-0000-0000-0000-000000000022"));
        state.cardInstances().put(firstCandidate, new CardInstance(firstCandidate, firstDefId, playerId, Zone.GRAVE));
        state.cardInstances().put(secondCandidate, new CardInstance(secondCandidate, secondDefId, playerId, Zone.GRAVE));
        player.grave().add(firstCandidate);
        player.grave().add(secondCandidate);
        player.pendingDecision(new PendingDecision.LastWordsChoice(
                "resolve last words choice",
                List.of(firstCandidate, secondCandidate),
                true,
                UUID.randomUUID()
        ));

        return new Fixture(state, ctx, playerId, player, firstCandidate, secondCandidate, firstEffect, secondEffect);
    }

    private record Fixture(
            GameState state,
            EngineContext ctx,
            PlayerId playerId,
            PlayerState player,
            CardInstId firstCandidate,
            CardInstId secondCandidate,
            TrackingLastWordsEffect firstEffect,
            TrackingLastWordsEffect secondEffect
    ) {}

    private static final class TrackingLastWordsEffect implements CardEffect {
        private final String label;
        private int lastWordsCalls;
        private PlayerId lastActor;
        private CardInstId lastSourceCardId;
        private boolean sawPendingClearedBeforeResolve;

        private TrackingLastWordsEffect(String label) {
            this.label = label;
        }

        @Override
        public String id() {
            return "TRACK_" + label;
        }

        @Override
        public void resolve(com.example.dueltower.engine.core.effect.EffectContext ec) {
        }

        @Override
        public void resolveLastWords(LastWordsContext lc) {
            lastWordsCalls++;
            lastActor = lc.actor();
            lastSourceCardId = lc.sourceCardId();
            sawPendingClearedBeforeResolve = lc.out().stream().anyMatch(event -> event instanceof GameEvent.PendingDecisionCleared cleared
                    && "LAST_WORDS".equals(cleared.type()));
            lc.out().add(new GameEvent.LogAppended("fixture last words " + label));
        }
    }
}
