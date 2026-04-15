package com.example.dueltower.screen.dto;

public record DeckEditorDraftCardDto(
        String key,
        String cardId,
        int count,
        int position
) {
}
