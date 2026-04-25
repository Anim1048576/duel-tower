package com.example.dueltower.character.repository;

import com.example.dueltower.character.domain.CharacterOwnedCardModifier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CharacterOwnedCardModifierRepository extends JpaRepository<CharacterOwnedCardModifier, Long> {

    List<CharacterOwnedCardModifier> findByOwnedCardId(String ownedCardId);

    List<CharacterOwnedCardModifier> findByOwnedCardIdIn(Collection<String> ownedCardIds);

    List<CharacterOwnedCardModifier> findByOwnedCardIdInOrderByIdAsc(Collection<String> ownedCardIds);

    void deleteByOwnedCardId(String ownedCardId);

    void deleteByOwnedCardIdIn(Collection<String> ownedCardIds);
}
