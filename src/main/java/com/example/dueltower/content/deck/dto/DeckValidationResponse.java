package com.example.dueltower.content.deck.dto;

import java.util.List;

public record DeckValidationResponse(
        boolean valid,
        List<DeckValidationIssue> issues,
        int normalizedTotalCards
) {}
