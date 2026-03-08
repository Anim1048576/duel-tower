package com.example.dueltower.content.deck.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.util.*;

import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.notNull;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "decks")
public class Deck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.PROTECTED)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeckType type;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Timestamp createDate;

    @UpdateTimestamp
    @Column(nullable = false)
    private Timestamp updateDate;

    @OneToMany(mappedBy = "deck", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id asc")
    private List<DeckCard> cards = new ArrayList<>();

    private Deck(String name, DeckType type) {
        this.name = normalizeName(name);
        this.type = requireType(type);
    }

    public static Deck create(String name, DeckType type) {
        return new Deck(name, type);
    }

    public void rename(String name) {
        this.name = normalizeName(name);
    }

    public void changeType(DeckType type) {
        this.type = requireType(type);
    }

    public List<DeckCard> getCards() {
        return Collections.unmodifiableList(cards);
    }

    public void putCard(String cardId, int count) {
        String normalizedCardId = normalizeCardId(cardId);
        DeckCard existing = findCard(normalizedCardId).orElse(null);
        if (existing == null) {
            cards.add(DeckCard.create(this, normalizedCardId, count));
            return;
        }
        existing.changeCount(count);
    }

    public void addCardCopies(String cardId, int delta) {
        String normalizedCardId = normalizeCardId(cardId);
        if (delta <= 0) {
            throw new IllegalArgumentException("delta must be >= 1");
        }

        DeckCard existing = findCard(normalizedCardId).orElse(null);
        if (existing == null) {
            cards.add(DeckCard.create(this, normalizedCardId, delta));
            return;
        }
        existing.changeCount(existing.getCount() + delta);
    }

    public void removeCard(String cardId) {
        String normalizedCardId = normalizeCardId(cardId);
        cards.removeIf(card -> card.hasCardId(normalizedCardId));
    }

    public void syncCards(Map<String, Integer> desired) {
        notNull(desired, "desired is required");

        Map<String, Integer> normalized = new LinkedHashMap<>();
        for (var entry : desired.entrySet()) {
            String cardId = normalizeCardId(entry.getKey());
            Integer count = entry.getValue();
            if (count == null || count <= 0) {
                throw new IllegalArgumentException("count must be >= 1: " + cardId);
            }
            normalized.put(cardId, count);
        }

        cards.removeIf(card -> !normalized.containsKey(card.getCardId()));

        for (var entry : normalized.entrySet()) {
            putCard(entry.getKey(), entry.getValue());
        }
    }

    private Optional<DeckCard> findCard(String cardId) {
        return cards.stream()
                .filter(card -> card.hasCardId(cardId))
                .findFirst();
    }

    private static String normalizeName(String name) {
        hasText(name, "name is required");
        return name.trim();
    }

    private static DeckType requireType(DeckType type) {
        notNull(type, "type is required");
        return type;
    }

    private static String normalizeCardId(String cardId) {
        hasText(cardId, "cardId is required");
        return cardId.trim();
    }
}
