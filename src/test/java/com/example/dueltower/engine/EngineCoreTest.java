package com.example.dueltower.engine;

import com.example.dueltower.engine.command.DrawCommand;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.core.CardEffect;
import com.example.dueltower.engine.core.CardEffectResolver;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class EngineCoreTest {

    private static final CardEffectResolver NOOP_EFFECTS = new CardEffectResolver() {
        @Override
        public boolean exists(String effectId) {
            return false;
        }

        @Override
        public CardEffect resolve(String effectId) {
            throw new IllegalStateException("NOOP resolver: effect should not be resolved in this test: " + effectId);
        }
    };

    @Test
    void draw_refill_from_grave_when_deck_empty() {
        SessionId sid = new SessionId(UUID.randomUUID());
        GameState state = new GameState(sid, 1234L);

        PlayerId p1 = new PlayerId("p1");
        PlayerState ps = new PlayerState(p1);
        state.players().put(p1, ps);

        CardDefId defA = new CardDefId("A");
        EngineContext ctx = new EngineContext(
                Map.of(defA, new CardDefinition(
                        defA,
                        "A",
                        CardType.SKILL,
                        1,
                        EnumSet.noneOf(Keyword.class),
                        "A",          // effectId (카드ID=effectId 규칙이면 이렇게)
                        Zone.GRAVE,   // resolveTo
                        false,        // token
                        "text"        // text
                )),
                NOOP_EFFECTS
        );

        CardInstId c1 = Ids.newCardInstId();
        CardInstId c2 = Ids.newCardInstId();
        state.cardInstances().put(c1, new CardInstance(c1, defA, p1, Zone.GRAVE));
        state.cardInstances().put(c2, new CardInstance(c2, defA, p1, Zone.GRAVE));
        ps.grave().addAll(List.of(c1, c2));

        GameEngine engine = new GameEngine();
        EngineResult r = engine.process(state, ctx, new DrawCommand(UUID.randomUUID(), state.version(), p1, 2));

        assertTrue(r.accepted());
        assertEquals(2, ps.hand().size());
        assertEquals(0, ps.grave().size());
        assertNotNull(r.events());
    }

    @Test
    void hand_limit_sets_pending_decision() {
        SessionId sid = new SessionId(UUID.randomUUID());
        GameState state = new GameState(sid, 9L);

        PlayerId p1 = new PlayerId("p1");
        PlayerState ps = new PlayerState(p1);
        state.players().put(p1, ps);

        CardDefId defA = new CardDefId("A");
        EngineContext ctx = new EngineContext(
                Map.of(defA, new CardDefinition(
                        defA,
                        "A",
                        CardType.SKILL,
                        1,
                        EnumSet.noneOf(Keyword.class),
                        "A",
                        Zone.GRAVE,
                        false,
                        "text"
                )),
                NOOP_EFFECTS
        );

        for (int i = 0; i < 7; i++) {
            CardInstId id = Ids.newCardInstId();
            state.cardInstances().put(id, new CardInstance(id, defA, p1, Zone.DECK));
            ps.deck().addLast(id);
        }

        GameEngine engine = new GameEngine();
        EngineResult r = engine.process(state, ctx, new DrawCommand(UUID.randomUUID(), state.version(), p1, 7));

        assertTrue(r.accepted());
        assertEquals(7, ps.hand().size());
        assertNotNull(ps.pendingDecision());
    }
}