package com.example.dueltower.content.deck.dto;

import com.example.dueltower.content.deck.domain.DeckType;

import java.util.List;

public record DeckResponse(
        Long id,
        String name,
        DeckType type,
        int totalCards,
        List<DeckCardDto> cards
) {}
