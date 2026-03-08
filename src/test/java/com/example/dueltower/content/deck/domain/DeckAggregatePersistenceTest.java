package com.example.dueltower.content.deck.domain;

import com.example.dueltower.content.deck.repository.DeckRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class DeckAggregatePersistenceTest {

    @Autowired
    private DeckRepository deckRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void putCard_updatesExistingRowInsteadOfInsertingDuplicate() {
        Deck deck = Deck.create("player-deck", DeckType.PLAYER);
        deck.putCard("Tig001_Card", 1);
        Deck saved = deckRepository.saveAndFlush(deck);

        saved.putCard("Tig001_Card", 3);
        deckRepository.flush();
        entityManager.clear();

        Deck reloaded = deckRepository.findWithCardsById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCards()).hasSize(1);
        assertThat(reloaded.getCards().get(0).getCardId()).isEqualTo("Tig001_Card");
        assertThat(reloaded.getCards().get(0).getCount()).isEqualTo(3);

        Number rowCount = (Number) entityManager.createNativeQuery(
                        "select count(*) from deck_cards where deck_id = :deckId and card_id = :cardId")
                .setParameter("deckId", saved.getId())
                .setParameter("cardId", "Tig001_Card")
                .getSingleResult();
        assertThat(rowCount.longValue()).isEqualTo(1L);
    }

    @Test
    void syncCards_updatesRemovesAddsWithoutDuplicateInsert() {
        Deck deck = Deck.create("sync-deck", DeckType.PLAYER);
        deck.syncCards(Map.of(
                "A001", 1,
                "B001", 2
        ));
        Deck saved = deckRepository.saveAndFlush(deck);

        Map<String, Integer> desired = new LinkedHashMap<>();
        desired.put("A001", 4); // 기존 카드 수량 변경
        desired.put("C001", 1); // 신규 카드 추가
        saved.syncCards(desired); // B001 제거

        deckRepository.flush();
        entityManager.clear();

        Deck reloaded = deckRepository.findWithCardsById(saved.getId()).orElseThrow();
        assertThat(reloaded.getCards()).hasSize(2);
        assertThat(reloaded.getCards())
                .extracting(DeckCard::getCardId, DeckCard::getCount)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple("A001", 4),
                        org.assertj.core.groups.Tuple.tuple("C001", 1)
                );

        Number aCount = (Number) entityManager.createNativeQuery(
                        "select count(*) from deck_cards where deck_id = :deckId and card_id = :cardId")
                .setParameter("deckId", saved.getId())
                .setParameter("cardId", "A001")
                .getSingleResult();
        assertThat(aCount.longValue()).isEqualTo(1L);
    }
}
