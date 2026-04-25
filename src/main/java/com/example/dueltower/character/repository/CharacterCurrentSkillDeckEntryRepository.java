package com.example.dueltower.character.repository;

import com.example.dueltower.character.domain.CharacterCurrentSkillDeckEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface CharacterCurrentSkillDeckEntryRepository extends JpaRepository<CharacterCurrentSkillDeckEntry, Long> {

    List<CharacterCurrentSkillDeckEntry> findByCharacterId(Long characterId);

    List<CharacterCurrentSkillDeckEntry> findByCharacterIdOrderByPositionAsc(Long characterId);

    void deleteByCharacterId(Long characterId);

    void deleteByOwnedCardId(String ownedCardId);

    void deleteByCharacterIdAndOwnedCardIdIn(Long characterId, Collection<String> ownedCardIds);

    boolean existsByCharacterIdAndOwnedCardId(Long characterId, String ownedCardId);
}
