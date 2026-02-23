package com.example.dueltower.engine.model;

import java.util.Objects;

public sealed interface PendingDecision permits PendingDecision.DiscardToHandLimit, PendingDecision.SearchPick {
    record DiscardToHandLimit(String reason, int limit) implements PendingDecision {
        public DiscardToHandLimit {
            Objects.requireNonNull(reason);
        }
    }

    record SearchPick(String reason, int pickCount) implements PendingDecision {
        public SearchPick {
            Objects.requireNonNull(reason);
        }
    }
}