package com.example.dueltower.character.repository;

import com.example.dueltower.character.domain.CharacterHiddenTrait;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CharacterHiddenTraitRepository extends JpaRepository<CharacterHiddenTrait, Long> {

    List<CharacterHiddenTrait> findByCharacterIdOrderByIdAsc(Long characterId);

    void deleteByCharacterId(Long characterId);
}
