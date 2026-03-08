package com.example.dueltower.content.deck.domain;

import jakarta.persistence.*;
import lombok.*;

import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.isTrue;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "deck_cards",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_deck_cards_deck_card", columnNames = {"deck_id", "card_id"})
        }
)
public class DeckCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.PROTECTED)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deck_id", nullable = false)
    private Deck deck;

    /** CardDefId.value (예: C001) */
    @Column(name = "card_id", nullable = false, length = 40)
    private String cardId;

    @Column(nullable = false)
    private int count;

    private DeckCard(Deck deck, String cardId, int count) {
        this.deck = deck;
        this.cardId = normalizeCardId(cardId);
        this.count = validateCount(count);
    }

    public static DeckCard create(Deck deck, String cardId, int count) {
        return new DeckCard(deck, cardId, count);
    }

    public void changeCount(int count) {
        this.count = validateCount(count);
    }

    public boolean hasCardId(String cardId) {
        return this.cardId.equals(normalizeCardId(cardId));
    }

    private static String normalizeCardId(String cardId) {
        hasText(cardId, "cardId is required");
        return cardId.trim();
    }

    private static int validateCount(int count) {
        isTrue(count > 0, "count must be >= 1");
        return count;
    }
}
