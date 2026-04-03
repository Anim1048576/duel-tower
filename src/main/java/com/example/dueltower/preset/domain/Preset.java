package com.example.dueltower.preset.domain;

import com.example.dueltower.character.domain.ListStringJsonConverter;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.springframework.util.Assert.hasText;
import static org.springframework.util.Assert.notNull;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "presets")
public class Preset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.PROTECTED)
    private Long id;

    @Column(nullable = false, length = 50)
    private String ownerUsername;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private Long characterId;

    @Convert(converter = ListStringJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> deckCardIds = new ArrayList<>();

    @Column(nullable = false, length = 100)
    private String exCardId;

    @Convert(converter = ListStringJsonConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private List<String> passiveIds = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Timestamp createDate;

    @UpdateTimestamp
    @Column(nullable = false)
    private Timestamp updateDate;

    private Preset(
            String ownerUsername,
            String name,
            Long characterId,
            List<String> deckCardIds,
            String exCardId,
            List<String> passiveIds
    ) {
        this.ownerUsername = normalizeOwnerUsername(ownerUsername);
        this.name = normalizeName(name);
        this.characterId = normalizeCharacterId(characterId);
        this.deckCardIds = normalizeList(deckCardIds);
        this.exCardId = normalizeCardId(exCardId, "exCardId is required");
        this.passiveIds = normalizeList(passiveIds);
    }

    public static Preset create(
            String ownerUsername,
            String name,
            Long characterId,
            List<String> deckCardIds,
            String exCardId,
            List<String> passiveIds
    ) {
        return new Preset(ownerUsername, name, characterId, deckCardIds, exCardId, passiveIds);
    }

    public void update(
            String name,
            Long characterId,
            List<String> deckCardIds,
            String exCardId,
            List<String> passiveIds
    ) {
        this.name = normalizeName(name);
        this.characterId = normalizeCharacterId(characterId);
        this.deckCardIds = normalizeList(deckCardIds);
        this.exCardId = normalizeCardId(exCardId, "exCardId is required");
        this.passiveIds = normalizeList(passiveIds);
    }

    public List<String> getDeckCardIds() {
        return Collections.unmodifiableList(deckCardIds);
    }

    public List<String> getPassiveIds() {
        return Collections.unmodifiableList(passiveIds);
    }

    private static String normalizeOwnerUsername(String ownerUsername) {
        hasText(ownerUsername, "ownerUsername is required");
        return ownerUsername.trim();
    }

    private static String normalizeName(String name) {
        hasText(name, "name is required");
        return name.trim();
    }

    private static Long normalizeCharacterId(Long characterId) {
        notNull(characterId, "characterId is required");
        if (characterId <= 0) {
            throw new IllegalArgumentException("characterId must be positive");
        }
        return characterId;
    }

    private static List<String> normalizeList(List<String> raw) {
        if (raw == null) {
            return new ArrayList<>();
        }
        List<String> result = new ArrayList<>();
        for (String item : raw) {
            result.add(normalizeCardId(item, "id must not be blank"));
        }
        return result;
    }

    private static String normalizeCardId(String raw, String message) {
        hasText(raw, message);
        return raw.trim();
    }
}
