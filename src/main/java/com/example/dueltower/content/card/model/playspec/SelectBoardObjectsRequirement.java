package com.example.dueltower.content.card.model.playspec;

import java.util.List;

public record SelectBoardObjectsRequirement(
        int minSelections,
        int maxSelections,
        List<BoardObjectKind> kinds,
        BoardObjectRelation relation,
        BoardObjectFilter filter,
        boolean excludeSourceCard
) implements ExtraPlayRequirement {
    public SelectBoardObjectsRequirement {
        if (minSelections < 0) {
            throw new IllegalArgumentException("minSelections must be >= 0");
        }
        if (maxSelections < minSelections) {
            throw new IllegalArgumentException("maxSelections must be >= minSelections");
        }
        kinds = kinds == null ? List.of() : List.copyOf(kinds);
        if (kinds.isEmpty()) {
            throw new IllegalArgumentException("kinds must not be empty");
        }
        relation = relation == null ? BoardObjectRelation.ANY : relation;
        filter = filter == null ? BoardObjectFilter.ANY : filter;
    }
}
