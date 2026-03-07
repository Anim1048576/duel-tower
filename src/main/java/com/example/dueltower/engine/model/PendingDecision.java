package com.example.dueltower.engine.model;

import java.util.Objects;
import java.util.UUID;

public sealed interface PendingDecision permits PendingDecision.DiscardToHandLimit, PendingDecision.SearchPick, PendingDecision.InitiativeTieOrder {
    record DiscardToHandLimit(String reason, int limit) implements PendingDecision {
        public DiscardToHandLimit {
            Objects.requireNonNull(reason);
        }
    }

    record SearchPick(
            String reason,
            java.util.List<Ids.CardInstId> candidateIds,
            int pickCount,
            Zone destination,
            boolean shuffleAfterPick,
            UUID correlationId
    ) implements PendingDecision {
        public SearchPick {
            Objects.requireNonNull(reason);
            Objects.requireNonNull(candidateIds);
            candidateIds = java.util.List.copyOf(candidateIds);
            Objects.requireNonNull(destination);
            if (pickCount <= 0 || pickCount > candidateIds.size()) {
                throw new IllegalArgumentException("pickCount must be between 1 and candidateIds.size()");
            }
            if (candidateIds.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("candidateIds must not contain null");
            }
            if (candidateIds.size() != new java.util.LinkedHashSet<>(candidateIds).size()) {
                throw new IllegalArgumentException("candidateIds must be unique");
            }
        }
    }

    record InitiativeTieOrder(String reason, int groupIndex, java.util.List<String> actorKeys) implements PendingDecision {
        public InitiativeTieOrder {
            Objects.requireNonNull(reason);
            Objects.requireNonNull(actorKeys);
        }
    }

}
