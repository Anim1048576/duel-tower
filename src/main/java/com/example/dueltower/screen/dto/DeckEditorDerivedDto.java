package com.example.dueltower.screen.dto;

public record DeckEditorDerivedDto(
        String title,
        String deckTypeLabel,
        int totalCards,
        boolean dirty
) {
}
