package com.example.dueltower.engine.command;

import com.example.dueltower.content.keyword.kdb.K014_LastWords;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.card.LastWordsContext;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardInstance;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.PendingDecision;
import com.example.dueltower.engine.model.PlayerState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class ResolveLastWordsCommand implements GameCommand {
    private final UUID commandId;
    private final long expectedVersion;
    private final PlayerId playerId;
    private final List<CardInstId> selectedIds;

    public ResolveLastWordsCommand(UUID commandId, long expectedVersion, PlayerId playerId, List<CardInstId> selectedIds) {
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
        if (!(ps.pendingDecision() instanceof PendingDecision.LastWordsChoice decision)) {
            errors.add("pending decision mismatch");
            return errors;
        }

        if (selectedIds.size() > 1) {
            errors.add("selectedIds must contain 0 or 1 card");
            return errors;
        }

        Set<CardInstId> unique = new HashSet<>();
        for (CardInstId id : selectedIds) {
            if (id == null) {
                errors.add("selected id is null");
                continue;
            }
            if (!unique.add(id)) {
                errors.add("duplicate selected id: " + id.value());
            }
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        if (selectedIds.isEmpty()) {
            if (!decision.skippable()) {
                errors.add("last words skip is not allowed");
            }
            return errors;
        }

        CardInstId selectedId = selectedIds.get(0);
        if (!decision.candidateIds().contains(selectedId)) {
            errors.add("selected id not in candidates: " + selectedId.value());
            return errors;
        }

        CardInstance ci = state.card(selectedId);
        if (ci == null) {
            errors.add("selected card instance missing: " + selectedId.value());
            return errors;
        }

        CardDefinition def;
        try {
            def = ctx.def(ci.defId());
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
            return errors;
        }

        int cost = KeywordOps.keywordValue(state, ctx, selectedId, K014_LastWords.ID);
        if (cost <= 0) {
            errors.add("last words cost must be positive");
        }
        if (ps.ap() < cost) {
            errors.add("not enough ap (need=" + cost + ", have=" + ps.ap() + ")");
        }

        try {
            CardEffect effect = ctx.effect(def.id());
            if (effect == null) {
                errors.add("missing CardEffect: " + def.id().value());
            }
        } catch (IllegalArgumentException e) {
            errors.add(e.getMessage());
        }

        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        PlayerState ps = state.player(playerId);
        PendingDecision.LastWordsChoice decision = (PendingDecision.LastWordsChoice) ps.pendingDecision();
        List<GameEvent> events = new ArrayList<>();

        if (selectedIds.isEmpty()) {
            ps.pendingDecision(null);
            events.add(new GameEvent.PendingDecisionCleared(ps.playerId().value(), "LAST_WORDS"));
            events.add(new GameEvent.LogAppended(ps.playerId().value() + " skipped last words"));
            return events;
        }

        CardInstId selectedId = selectedIds.get(0);
        int cost = KeywordOps.keywordValue(state, ctx, selectedId, K014_LastWords.ID);
        ps.ap(ps.ap() - cost);

        CardInstance ci = state.card(selectedId);
        CardDefinition def = ctx.def(ci.defId());
        CardEffect effect = ctx.effect(def.id());
        effect.resolveLastWords(new LastWordsContext(state, ctx, ps.playerId(), selectedId, events));

        ps.pendingDecision(null);
        events.add(new GameEvent.PendingDecisionCleared(ps.playerId().value(), "LAST_WORDS"));
        events.add(new GameEvent.LogAppended(
                ps.playerId().value() + " resolved last words " + selectedId.value()
        ));
        return events;
    }
}
