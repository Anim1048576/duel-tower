package com.example.dueltower.engine.core;

import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;

import java.util.*;

public final class ZoneOps {
    private ZoneOps() {}

    public static void drawWithRefill(GameState state, EngineContext ctx, PlayerState ps, int count, List<GameEvent> events) {
        for (int i = 0; i < count; i++) {
            if (ps.deck().isEmpty()) {
                refillDeckFromGrave(state, ps, events);
                shuffleDeck(state, ps, events, deriveShuffleRandom(state, ps));
            }
            if (ps.deck().isEmpty()) {
                return;
            }
            CardInstId top = ps.deck().removeFirst();
            ps.hand().add(top);
            state.card(top).zone(Zone.HAND);
        }
    }

    public static void refillDeckFromGrave(GameState state, PlayerState ps, List<GameEvent> events) {
        if (ps.grave().isEmpty()) return;
        for (CardInstId id : ps.grave()) {
            ps.deck().addLast(id);
            state.card(id).zone(Zone.DECK);
        }
        ps.grave().clear();
        events.add(new GameEvent.DeckRefilled(ps.playerId().value()));
    }

    public static void shuffleDeck(GameState state, PlayerState ps, List<GameEvent> events, Random rnd) {
        List<CardInstId> list = new ArrayList<>(ps.deck());
        ps.deck().clear();
        Collections.shuffle(list, rnd);
        for (CardInstId id : list) ps.deck().addLast(id);
        events.add(new GameEvent.DeckShuffled(ps.playerId().value()));
    }

    public static void moveHandToGrave(GameState state, PlayerState ps, CardInstId id, List<GameEvent> events) {
        ps.hand().remove(id);
        CardInstance ci = state.card(id);
        ci.zone(Zone.GRAVE);
        events.add(new GameEvent.CardsMoved(ps.playerId().value(), "HAND", "GRAVE", 1));

        ps.grave().add(id);
    }

    public static void moveToZoneOrVanishIfToken(GameState state, EngineContext ctx, PlayerState ps, CardInstId id, Zone from, Zone to, List<GameEvent> events) {
        CardInstance ci = state.card(id);
        CardDefinition def = ctx.def(ci.defId());

        if (def.token() && (to == Zone.DECK || to == Zone.GRAVE || to == Zone.EXCLUDED || to == Zone.EX)) {
            removeFromZone(ps, id, from);
            state.cardInstances().remove(id);
            events.add(new GameEvent.LogAppended("token vanished"));
            return;
        }

        removeFromZone(ps, id, from);
        addToZone(ps, id, to);
        ci.zone(to);
        events.add(new GameEvent.CardsMoved(ps.playerId().value(), from.name(), to.name(), 1));
    }

    private static void removeFromZone(PlayerState ps, CardInstId id, Zone from) {
        switch (from) {
            case HAND -> ps.hand().remove(id);
            case GRAVE -> ps.grave().remove(id);
            case FIELD -> ps.field().remove(id);
            case EXCLUDED -> ps.excluded().remove(id);
            case DECK -> ps.deck().remove(id);
            case EX -> { if (Objects.equals(ps.exCard(), id)) ps.exCard(null); }
        }
    }

    private static void addToZone(PlayerState ps, CardInstId id, Zone to) {
        switch (to) {
            case HAND -> ps.hand().add(id);
            case GRAVE -> ps.grave().add(id);
            case FIELD -> ps.field().add(id);
            case EXCLUDED -> ps.excluded().add(id);
            case DECK -> ps.deck().addLast(id);
            case EX -> ps.exCard(id);
        }
    }

    private static Random deriveShuffleRandom(GameState state, PlayerState ps) {
        long mix = state.seed() ^ state.version() ^ ps.playerId().value().hashCode();
        return new Random(mix);
    }
}