package com.example.dueltower.screen.dto;

import com.example.dueltower.content.deck.domain.DeckType;

import java.util.List;

public record DeckEditorDraftDto(
        String name,
        DeckType type,
        List<DeckEditorDraftCardDto> cards
) {
}
