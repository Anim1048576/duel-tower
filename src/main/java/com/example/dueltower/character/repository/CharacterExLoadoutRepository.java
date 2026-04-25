package com.example.dueltower.character.repository;

import com.example.dueltower.character.domain.CharacterExLoadout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CharacterExLoadoutRepository extends JpaRepository<CharacterExLoadout, Long> {

    List<CharacterExLoadout> findByCharacterId(Long characterId);

    void deleteByCharacterId(Long characterId);
}
