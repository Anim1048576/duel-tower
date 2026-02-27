package com.example.dueltower.content.deck.repository;

import com.example.dueltower.content.deck.domain.Deck;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeckRepository extends JpaRepository<Deck, Long> {

    @Override
    @EntityGraph(attributePaths = {"cards"})
    List<Deck> findAll();

    @EntityGraph(attributePaths = {"cards"})
    Optional<Deck> findWithCardsById(Long id);
}
