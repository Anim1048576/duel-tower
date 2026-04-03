package com.example.dueltower.preset.repository;

import com.example.dueltower.preset.domain.Preset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PresetRepository extends JpaRepository<Preset, Long> {
    List<Preset> findAllByOwnerUsernameOrderByIdAsc(String ownerUsername);

    Optional<Preset> findByIdAndOwnerUsername(Long id, String ownerUsername);
}
