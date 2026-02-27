package com.example.dueltower.content.deck.dto;

import com.example.dueltower.content.deck.domain.DeckType;

import java.util.List;

public record UpdateDeckRequest(
        String name,
        DeckType type,
        List<DeckCardSpec> cards
) {}
