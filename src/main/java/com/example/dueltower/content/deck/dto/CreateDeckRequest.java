package com.example.dueltower.content.deck.dto;

import com.example.dueltower.content.deck.DeckType;

import java.util.List;

public record CreateDeckRequest(
        String name,
        DeckType type,
        List<DeckCardSpec> cards
) {}
