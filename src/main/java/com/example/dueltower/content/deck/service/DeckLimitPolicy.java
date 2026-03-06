package com.example.dueltower.content.deck.service;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.engine.model.Ids;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class DeckLimitPolicy {
    public static final int PLAYER_TOTAL_CARDS = 12;
    public static final int PLAYER_DEFAULT_MAX_COPIES = 3;

    private final CardService cardService;

    public DeckLimitPolicy(CardService cardService) {
        this.cardService = cardService;
    }

    public int maxCopiesFor(String cardId) {
        Integer override = cardService.maxDeckCopies(new Ids.CardDefId(cardId));
        return (override == null) ? PLAYER_DEFAULT_MAX_COPIES : override;
    }

    public void validatePlayerDeckExact(Map<String, Integer> merged) {
        int total = merged.values().stream().mapToInt(Integer::intValue).sum();
        if (total != PLAYER_TOTAL_CARDS) {
            throw DeckLimitViolation.playerDeckMustHaveExact(total, PLAYER_TOTAL_CARDS);
        }
        validateCopiesByCard(merged);
    }

    public void validatePlayerDeckUpTo(Map<String, Integer> merged) {
        int total = merged.values().stream().mapToInt(Integer::intValue).sum();
        if (total > PLAYER_TOTAL_CARDS) {
            throw DeckLimitViolation.playerDeckCannotExceedTotal(total, PLAYER_TOTAL_CARDS);
        }
        validateCopiesByCard(merged);
    }

    private void validateCopiesByCard(Map<String, Integer> merged) {
        for (var e : merged.entrySet()) {
            int max = maxCopiesFor(e.getKey());
            if (e.getValue() > max) {
                throw DeckLimitViolation.playerDeckCardCopyExceeded(e.getKey(), e.getValue(), max);
            }
        }
    }
}
