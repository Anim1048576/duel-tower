package com.example.dueltower.content.deck.dto;

import java.util.List;

public record RemoveDeckCardsRequest(
        List<DeckCardSpec> cards
) {
}
