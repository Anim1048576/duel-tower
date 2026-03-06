package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.combat.TurnFlow;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;

import java.util.*;
import java.util.stream.Collectors;

public final class ResolveInitiativeTieCommand implements GameCommand {
    private final UUID commandId;
    private final long expectedVersion;
    private final Ids.PlayerId playerId;
    private final int groupIndex;
    private final List<String> orderedActorKeys;

    public ResolveInitiativeTieCommand(
            UUID commandId,
            long expectedVersion,
            Ids.PlayerId playerId,
            int groupIndex,
            List<String> orderedActorKeys
    ) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.playerId = playerId;
        this.groupIndex = groupIndex;
        this.orderedActorKeys = (orderedActorKeys == null) ? List.of() : List.copyOf(orderedActorKeys);
    }

    @Override public UUID commandId() { return commandId; }
    @Override public long expectedVersion() { return expectedVersion; }

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        CombatState cs = state.combat();
        if (cs == null) {
            errors.add("combat not started");
            return errors;
        }
        if (cs.phase() != CombatPhase.INITIATIVE_TIE_DECISION) {
            errors.add("invalid phase: " + cs.phase());
        }

        PlayerState ps = state.player(playerId);
        if (ps == null) {
            errors.add("player not found");
            return errors;
        }

        if (groupIndex < 0 || groupIndex >= cs.initiativeTieGroups().size()) {
            errors.add("invalid tie group index");
            return errors;
        }

        List<String> group = cs.initiativeTieGroups().get(groupIndex);
        String requester = CombatState.actorKey(TargetRef.ofPlayer(playerId));
        if (!group.contains(requester)) {
            errors.add("player is not in target tie group");
        }

        if (ps.pendingDecision() == null) {
            errors.add("no pending decision");
        } else if (!(ps.pendingDecision() instanceof PendingDecision.InitiativeTieOrder tie)
                || tie.groupIndex() != groupIndex) {
            errors.add("pending decision mismatch");
        }

        if (orderedActorKeys.isEmpty()) {
            errors.add("orderedActorKeys is required");
            return errors;
        }

        Set<String> submittedSet = new LinkedHashSet<>(orderedActorKeys);
        if (submittedSet.size() != orderedActorKeys.size()) {
            errors.add("orderedActorKeys has duplicates");
        }

        Set<String> expectedSet = new LinkedHashSet<>(group);
        if (!submittedSet.equals(expectedSet)) {
            errors.add("orderedActorKeys must match tie group members exactly");
        }

        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        List<GameEvent> events = new ArrayList<>();
        CombatState cs = state.combat();
        List<String> group = List.copyOf(cs.initiativeTieGroups().get(groupIndex));

        applyResolvedOrder(cs, group, orderedActorKeys);

        for (String actorKey : group) {
            Ids.PlayerId tiedPlayer = parsePlayerActorKey(actorKey);
            PlayerState tiedPs = state.player(tiedPlayer);
            if (tiedPs == null) continue;
            tiedPs.pendingDecision(null);
            events.add(new GameEvent.PendingDecisionCleared(tiedPlayer.value(), "INITIATIVE_TIE_ORDER"));
        }

        cs.initiativeTieGroups().remove(groupIndex);
        reindexInitiativeTiePendingDecisions(state, cs);

        events.add(new GameEvent.LogAppended("initiative tie resolved group=" + groupIndex + " order=" + orderedActorKeys));

        if (cs.initiativeTieGroups().isEmpty()) {
            TurnFlow.initializeFirstTurn(state, ctx, events);
            events.add(new GameEvent.LogAppended("all initiative ties resolved"));
            events.add(new GameEvent.TurnAdvanced(CombatState.actorKey(cs.currentTurnActor()), cs.round()));
        }

        return events;
    }

    private static void applyResolvedOrder(CombatState cs, List<String> originalGroup, List<String> resolvedOrder) {
        Map<String, TargetRef> byKey = cs.turnOrder().stream()
                .collect(Collectors.toMap(CombatState::actorKey, ref -> ref, (a, b) -> a, LinkedHashMap::new));

        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < cs.turnOrder().size(); i++) {
            String key = CombatState.actorKey(cs.turnOrder().get(i));
            if (originalGroup.contains(key)) {
                indices.add(i);
            }
        }

        for (int i = 0; i < indices.size(); i++) {
            int idx = indices.get(i);
            String key = resolvedOrder.get(i);
            TargetRef ref = byKey.get(key);
            if (ref != null) {
                cs.turnOrder().set(idx, ref);
            }
        }
    }

    private static void reindexInitiativeTiePendingDecisions(GameState state, CombatState cs) {
        for (int idx = 0; idx < cs.initiativeTieGroups().size(); idx++) {
            List<String> group = cs.initiativeTieGroups().get(idx);
            for (String actorKey : group) {
                Ids.PlayerId pid = parsePlayerActorKey(actorKey);
                PlayerState ps = state.player(pid);
                if (ps == null) continue;
                ps.pendingDecision(new PendingDecision.InitiativeTieOrder(
                        "resolve initiative tie order",
                        idx,
                        List.copyOf(group)
                ));
            }
        }
    }

    private static Ids.PlayerId parsePlayerActorKey(String actorKey) {
        if (actorKey == null || !actorKey.startsWith("P:")) {
            throw new IllegalStateException("tie group must contain only players: " + actorKey);
        }
        return new Ids.PlayerId(actorKey.substring(2));
    }
}
