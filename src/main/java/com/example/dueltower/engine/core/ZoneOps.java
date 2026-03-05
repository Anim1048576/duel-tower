package com.example.dueltower.engine.core;

import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.core.effect.card.FieldEffectOps;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.core.effect.keyword.MoveReason;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardInstId;

import java.util.*;

public final class ZoneOps {
    private ZoneOps() {}

    /**
     * Validate zone invariants and throw if something looks inconsistent.
     *
     * This is intended for development/debug use. Call it from the engine after a command is applied.
     */
    public static void assertInvariants(GameState state) {
        List<String> issues = validateInvariants(state);
        if (!issues.isEmpty()) {
            StringBuilder sb = new StringBuilder("Zone invariants violated (" + issues.size() + ")\n");
            int limit = Math.min(issues.size(), 20);
            for (int i = 0; i < limit; i++) {
                sb.append("- ").append(issues.get(i)).append('\n');
            }
            if (issues.size() > limit) sb.append("- ...");
            throw new IllegalStateException(sb.toString());
        }
    }

    /** Returns human-readable invariant violations (empty if OK). */
    public static List<String> validateInvariants(GameState state) {
        if (state == null) return List.of("state is null");

        List<String> issues = new ArrayList<>();
        Map<CardInstId, Zone> membership = new HashMap<>();

        for (PlayerState ps : state.players().values()) {
            if (ps == null) continue;

            for (CardInstId id : ps.deck()) recordMembership(state, issues, membership, ps, id, Zone.DECK);
            for (CardInstId id : ps.hand()) recordMembership(state, issues, membership, ps, id, Zone.HAND);
            for (CardInstId id : ps.grave()) recordMembership(state, issues, membership, ps, id, Zone.GRAVE);
            for (CardInstId id : ps.field()) recordMembership(state, issues, membership, ps, id, Zone.FIELD);
            for (CardInstId id : ps.excluded()) recordMembership(state, issues, membership, ps, id, Zone.EXCLUDED);
            if (ps.exCard() != null) recordMembership(state, issues, membership, ps, ps.exCard(), Zone.EX);
        }

        // Every existing card instance should belong to exactly one zone of its owner.
        for (Map.Entry<CardInstId, CardInstance> e : state.cardInstances().entrySet()) {
            CardInstId id = e.getKey();
            CardInstance ci = e.getValue();
            if (ci == null) {
                issues.add("cardInstances[" + safeId(id) + "] is null");
                continue;
            }

            PlayerState owner = state.player(ci.ownerId());
            if (owner == null) {
                issues.add("card " + safeId(id) + " owner missing: " + ci.ownerId().value());
                continue;
            }

            Zone z = membership.get(id);
            if (z == null) {
                issues.add("card " + safeId(id) + " exists but not present in any owner zone list (owner=" + ci.ownerId().value() + ", zone=" + ci.zone() + ")");
                continue;
            }
            if (ci.zone() != z) {
                issues.add("card " + safeId(id) + " zone mismatch: instance=" + ci.zone() + ", list=" + z + " (owner=" + ci.ownerId().value() + ")");
            }
        }


        for (PlayerState ps : state.players().values()) {
            for (Ids.SummonInstId summonId : ps.activeSummons()) {
                SummonState summon = state.summon(summonId);
                if (summon == null) {
                    issues.add("active summon missing state: " + summonId.value() + " (owner=" + ps.playerId().value() + ")");
                    continue;
                }
                if (!Objects.equals(summon.owner(), ps.playerId())) {
                    issues.add("summon owner mismatch: " + summonId.value());
                }
                if (!ps.field().contains(summon.sourceCardId())) {
                    issues.add("summon source card not on field: " + summon.sourceCardId().value());
                }
            }
        }

        return issues;
    }

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

    // 기존 시그니처는 호환용으로 남겨둠 (from은 무시하고 CardInstance.zone()을 기준으로 처리)
    public static void moveToZoneOrVanishIfToken(GameState state, EngineContext ctx, PlayerState ps, CardInstId id, Zone from, Zone to, List<GameEvent> events) {
        moveToZoneOrVanishIfToken(state, ctx, ps, id, to, events, MoveReason.OTHER);
    }

    public static void moveToZoneOrVanishIfToken(GameState state, EngineContext ctx, PlayerState ps, CardInstId id, Zone from, Zone to, List<GameEvent> events, MoveReason reason) {
        moveToZoneOrVanishIfToken(state, ctx, ps, id, to, events, reason);
    }

    /**
     * Move a card to the given zone. The current zone is derived from the card instance.
     * This avoids callers accidentally providing a wrong "from".
     */
    public static void moveToZoneOrVanishIfToken(GameState state, EngineContext ctx, PlayerState ps, CardInstId id, Zone to, List<GameEvent> events) {
        moveToZoneOrVanishIfToken(state, ctx, ps, id, to, events, MoveReason.OTHER);
    }

    public static void moveToZoneOrVanishIfToken(GameState state, EngineContext ctx, PlayerState ps, CardInstId id, Zone to, List<GameEvent> events, MoveReason reason) {
        if (id == null || ps == null || state == null) return;
        CardInstance ci = state.card(id);
        if (ci == null) return;

        Zone from = ci.zone();
        CardDefinition def = ctx.def(ci.defId());

        Zone finalTo = KeywordOps.overrideMoveDestination(state, ctx, ps, id, from, to, reason);

        boolean leavingField = from == Zone.FIELD && finalTo != Zone.FIELD;
        if (leavingField) {
            FieldEffectOps.onLeaveField(state, ctx, ps, id, events, "MOVE_LEAVE_FIELD");
            SummonOps.destroySummonForCard(state, ps, id);
        }

        if (def.token() && (finalTo == Zone.DECK || finalTo == Zone.GRAVE || finalTo == Zone.EXCLUDED || finalTo == Zone.EX)) {
            removeFromZone(ps, id, from);
            state.cardInstances().remove(id);
            events.add(new GameEvent.LogAppended("token vanished"));
            return;
        }

        removeFromZone(ps, id, from);
        addToZone(ps, id, finalTo);
        ci.zone(finalTo);
        events.add(new GameEvent.CardsMoved(ps.playerId().value(), from.name(), finalTo.name(), 1));

        boolean enteringField = from != Zone.FIELD && finalTo == Zone.FIELD;
        if (enteringField) {
            FieldEffectOps.onEnterField(state, ctx, ps, id, events, "MOVE_ENTER_FIELD");
        }
    }

    private static void recordMembership(GameState state, List<String> issues, Map<CardInstId, Zone> membership, PlayerState ps, CardInstId id, Zone z) {
        if (id == null) {
            issues.add("null card id in " + ps.playerId().value() + " " + z);
            return;
        }

        CardInstance ci = state.card(id);
        if (ci == null) {
            issues.add("zone list references missing card instance: " + safeId(id) + " (owner=" + ps.playerId().value() + ", zone=" + z + ")");
        } else {
            if (!Objects.equals(ci.ownerId(), ps.playerId())) {
                issues.add("card " + safeId(id) + " is in " + ps.playerId().value() + " zone list but owner is " + ci.ownerId().value());
            }
            if (ci.zone() != z) {
                issues.add("card " + safeId(id) + " zone mismatch: instance=" + ci.zone() + ", list=" + z + " (owner=" + ps.playerId().value() + ")");
            }
        }

        Zone prev = membership.putIfAbsent(id, z);
        if (prev != null && prev != z) {
            issues.add("card " + safeId(id) + " appears in multiple zones: " + prev + " and " + z + " (owner=" + ps.playerId().value() + ")");
        }
    }

    private static String safeId(CardInstId id) {
        return (id == null) ? "<null>" : String.valueOf(id.value());
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
