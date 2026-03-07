package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.ZoneOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.PendingDecision;
import com.example.dueltower.engine.model.PlayerState;

import java.util.*;

public final class ResolveSearchPickCommand implements GameCommand {
    private final UUID commandId;
    private final long expectedVersion;
    private final PlayerId playerId;
    private final List<CardInstId> selectedIds;

    public ResolveSearchPickCommand(UUID commandId, long expectedVersion, PlayerId playerId, List<CardInstId> selectedIds) {
        this.commandId = commandId;
        this.expectedVersion = expectedVersion;
        this.playerId = playerId;
        this.selectedIds = (selectedIds == null) ? List.of() : List.copyOf(selectedIds);
    }

    @Override public UUID commandId() { return commandId; }
    @Override public long expectedVersion() { return expectedVersion; }

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        PlayerState ps = state.player(playerId);
        if (ps == null) return List.of("player not found");

        if (ps.pendingDecision() == null) {
            errors.add("no pending decision");
            return errors;
        }
        if (!(ps.pendingDecision() instanceof PendingDecision.SearchPick sp)) {
            errors.add("pending decision mismatch");
            return errors;
        }

        if (selectedIds.size() != sp.pickCount()) {
            errors.add("selected count mismatch (need=" + sp.pickCount() + ")");
        }

        Set<CardInstId> uniq = new HashSet<>();
        for (CardInstId id : selectedIds) {
            if (id == null) {
                errors.add("selected id is null");
                continue;
            }
            if (!uniq.add(id)) {
                errors.add("duplicate selected id: " + id.value());
            }
        }

        Set<CardInstId> candidates = new HashSet<>(sp.candidateIds());
        for (CardInstId id : selectedIds) {
            if (id == null) continue;
            if (!candidates.contains(id)) {
                errors.add("selected id not in candidates: " + id.value());
            }
        }

        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState ps = state.player(playerId);
        PendingDecision.SearchPick sp = (PendingDecision.SearchPick) ps.pendingDecision();
        List<GameEvent> events = new ArrayList<>();

        for (CardInstId id : selectedIds) {
            ZoneOps.moveToZoneOrVanishIfToken(state, ctx, ps, id, sp.destination(), events);
        }

        if (sp.shuffleAfterPick()) {
            ZoneOps.shuffleDeck(state, ps, events, new Random(state.seed() ^ ps.playerId().value().hashCode() ^ state.version()));
            events.add(new GameEvent.LogAppended(ps.playerId().value() + " shuffles deck after search pick"));
        }

        ps.pendingDecision(null);
        events.add(new GameEvent.PendingDecisionCleared(ps.playerId().value(), "SEARCH_PICK"));
        events.add(new GameEvent.LogAppended(ps.playerId().value() + " resolved search pick " + selectedIds.size()));
        return events;
    }
}
