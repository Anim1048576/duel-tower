package com.example.dueltower.engine.core.effect;

import com.example.dueltower.engine.model.Ids;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class LastWordsBatchCollector {
    private final UUID correlationId;
    private final LinkedHashSet<Ids.CardInstId> candidateIds = new LinkedHashSet<>();

    public LastWordsBatchCollector() {
        this(UUID.randomUUID());
    }

    public LastWordsBatchCollector(UUID correlationId) {
        this.correlationId = Objects.requireNonNull(correlationId);
    }

    public UUID correlationId() {
        return correlationId;
    }

    public void register(Ids.CardInstId id) {
        if (id == null) {
            return;
        }
        candidateIds.add(id);
    }

    public boolean hasCandidates() {
        return !candidateIds.isEmpty();
    }

    public List<Ids.CardInstId> candidateIds() {
        return List.copyOf(candidateIds);
    }

    public void clear() {
        candidateIds.clear();
    }
}
