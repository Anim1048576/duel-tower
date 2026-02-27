package com.example.dueltower.content.deck.domain;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
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
}
