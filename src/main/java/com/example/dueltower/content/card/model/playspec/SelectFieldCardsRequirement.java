package com.example.dueltower.content.card.model.playspec;

/**
 * Legacy declaration for selectedIds-based field-card selection metadata.
 * New card specs should prefer SelectBoardObjectsRequirement(FIELD_CARD).
 */
@Deprecated(forRemoval = false)
public record SelectFieldCardsRequirement(
        int minSelections,
        int maxSelections,
        FieldCardSelectionScope scope,
        FieldCardFilter filter,
        boolean excludeSourceCard
) implements ExtraPlayRequirement {
    public SelectFieldCardsRequirement {
        if (minSelections < 0) {
            throw new IllegalArgumentException("minSelections must be >= 0");
        }
        if (maxSelections < minSelections) {
            throw new IllegalArgumentException("maxSelections must be >= minSelections");
        }
        scope = scope == null ? FieldCardSelectionScope.ALL_PLAYER_FIELDS : scope;
        filter = filter == null ? FieldCardFilter.INSTALLED_ONLY : filter;
    }
}
