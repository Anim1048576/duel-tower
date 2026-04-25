package com.example.dueltower.character.repository;

import com.example.dueltower.character.domain.CharacterOwnedCard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CharacterOwnedCardRepository extends JpaRepository<CharacterOwnedCard, String> {

    List<CharacterOwnedCard> findByCharacterId(Long characterId);

    List<CharacterOwnedCard> findByCharacterIdOrderByCreateDateAscOwnedCardIdAsc(Long characterId);

    void deleteByCharacterId(Long characterId);

    void deleteByCharacterIdAndOwnedCardIdIn(Long characterId, Collection<String> ownedCardIds);

    boolean existsByCharacterIdAndOwnedCardId(Long characterId, String ownedCardId);
}
