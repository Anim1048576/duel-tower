package com.example.dueltower.character.repository;

import com.example.dueltower.character.domain.CharacterProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CharacterProfileRepository extends JpaRepository<CharacterProfile, Long> {
}
