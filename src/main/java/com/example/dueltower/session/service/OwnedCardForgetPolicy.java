package com.example.dueltower.session.service;

import com.example.dueltower.content.card.model.OwnedCard;

import java.util.List;
import java.util.Map;

public final class OwnedCardForgetPolicy {
    private OwnedCardForgetPolicy() {}

    public static ForgetCheck evaluate(OwnedCard card, Map<String, Integer> ownedCounts, Map<String, Integer> deckCounts) {
        if (card.strengthened()) {
            return ForgetCheck.blocked("cannot forget strengthened card");
        }
        if (card.weakened()) {
            return ForgetCheck.blocked("cannot forget weakened card");
        }
        if (card.lockedInDeck()) {
            return ForgetCheck.blocked("cannot forget locked-in-deck card");
        }

        int ownedCount = ownedCounts.getOrDefault(card.cardId(), 0);
        int deckCount = deckCounts.getOrDefault(card.cardId(), 0);
        if (ownedCount - 1 < deckCount) {
            return ForgetCheck.blocked(
                    "cannot forget card required by current deck: "
                            + card.cardId()
                            + " (owned " + ownedCount + ", deck " + deckCount + ")"
            );
        }

        return ForgetCheck.allowed();
    }

    public static boolean hasForgettableCard(List<OwnedCard> ownedCards,
                                             Map<String, Integer> ownedCounts,
                                             Map<String, Integer> deckCounts) {
        for (OwnedCard card : ownedCards) {
            if (evaluate(card, ownedCounts, deckCounts).forgettable()) {
                return true;
            }
        }
        return false;
    }

    public record ForgetCheck(boolean forgettable, String reason) {
        public static ForgetCheck allowed() {
            return new ForgetCheck(true, null);
        }

        public static ForgetCheck blocked(String reason) {
            return new ForgetCheck(false, reason);
        }
    }
}
