package com.example.dueltower.screen.service;

import com.example.dueltower.content.deck.domain.DeckType;
import com.example.dueltower.content.deck.dto.DeckValidationIssue;
import com.example.dueltower.content.deck.dto.DeckValidationResponse;
import com.example.dueltower.screen.dto.DeckEditorDraftCardDto;
import com.example.dueltower.screen.dto.DeckEditorDraftDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScreenResponseFactoryDeckEditorValidationTest {

    private final ScreenResponseFactory factory = new ScreenResponseFactory();

    @Test
    void deckEditorValidationIsFreshImmediatelyAfterValidation() {
        DeckEditorDraftDto draft = draft(
                DeckType.PLAYER,
                new DeckEditorDraftCardDto("deck-card-1", "C001", 2, 1)
        );

        var validation = factory.deckEditorValidation(new DeckValidationResponse(true, List.of(), 2), draft);

        assertThat(validation.isStale()).isFalse();
        assertThat(validation.validatedDraftSignature()).isEqualTo("type=PLAYER;cards=C001:2");
    }

    @Test
    void validatedDraftSignatureChangesWhenCardCountChanges() {
        var before = factory.deckEditorValidation(
                new DeckValidationResponse(true, List.of(), 2),
                draft(DeckType.PLAYER, new DeckEditorDraftCardDto("deck-card-1", "C001", 2, 1))
        );
        var after = factory.deckEditorValidation(
                new DeckValidationResponse(true, List.of(), 3),
                draft(DeckType.PLAYER, new DeckEditorDraftCardDto("deck-card-1", "C001", 3, 1))
        );

        assertThat(after.validatedDraftSignature()).isNotEqualTo(before.validatedDraftSignature());
    }

    @Test
    void validatedDraftSignatureChangesWhenCardOrderChanges() {
        var ordered = factory.deckEditorValidation(
                new DeckValidationResponse(true, List.of(), 2),
                draft(
                        DeckType.PLAYER,
                        new DeckEditorDraftCardDto("deck-card-1", "C001", 1, 1),
                        new DeckEditorDraftCardDto("deck-card-2", "C002", 1, 2)
                )
        );
        var reordered = factory.deckEditorValidation(
                new DeckValidationResponse(true, List.of(), 2),
                draft(
                        DeckType.PLAYER,
                        new DeckEditorDraftCardDto("deck-card-1", "C002", 1, 1),
                        new DeckEditorDraftCardDto("deck-card-2", "C001", 1, 2)
                )
        );

        assertThat(reordered.validatedDraftSignature()).isNotEqualTo(ordered.validatedDraftSignature());
    }

    @Test
    void validatedDraftSignatureChangesWhenDeckTypeChanges() {
        var player = factory.deckEditorValidation(
                new DeckValidationResponse(true, List.of(), 1),
                draft(DeckType.PLAYER, new DeckEditorDraftCardDto("deck-card-1", "C001", 1, 1))
        );
        var enemy = factory.deckEditorValidation(
                new DeckValidationResponse(false, List.of(new DeckValidationIssue("X", "Y", null)), 1),
                draft(DeckType.ENEMY, new DeckEditorDraftCardDto("deck-card-1", "C001", 1, 1))
        );

        assertThat(enemy.validatedDraftSignature()).isNotEqualTo(player.validatedDraftSignature());
    }

    private DeckEditorDraftDto draft(DeckType type, DeckEditorDraftCardDto... cards) {
        return new DeckEditorDraftDto("test-deck", type, List.of(cards));
    }
}
