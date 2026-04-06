package com.example.dueltower.content.card.model.playspec;

public record DiscardFromHandRequirement(
        int count,
        boolean excludeSourceCard,
        DiscardFilter filter
) implements ExtraPlayRequirement {
    public DiscardFromHandRequirement {
        if (count < 1) {
            throw new IllegalArgumentException("discard count must be >= 1");
        }
        filter = filter == null ? DiscardFilter.ANY : filter;
    }
}
