package com.example.dueltower.screen.dto;

import com.example.dueltower.content.deck.dto.DeckValidationIssue;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Server-side validation snapshot for the last validated deck-editor draft.
 *
 * <p>This DTO does not describe the user's current local editor freshness.
 * Frontend code must compare the current local draft with {@code validatedDraftSignature}
 * to determine whether the validation result is stale for the local editor.</p>
 *
 * <p>{@code validatedDraftSignature} is a canonical summary of the validated draft's
 * editor-relevant shape: deck type plus ordered card entries and counts.</p>
 */
public record DeckEditorValidationDto(
        boolean valid,
        int normalizedTotalCards,
        List<DeckValidationIssue> issues,
        String validatedDraftSignature,
        OffsetDateTime validatedAt
) {
}
