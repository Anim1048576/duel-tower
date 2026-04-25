package com.example.dueltower.character.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "character_current_skill_deck_entries",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_character_current_skill_deck_entries_character_position",
                        columnNames = {"character_id", "position"}
                ),
                @UniqueConstraint(
                        name = "uk_character_current_skill_deck_entries_character_owned_card",
                        columnNames = {"character_id", "owned_card_id"}
                )
        }
)
public class CharacterCurrentSkillDeckEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.PROTECTED)
    private Long id;

    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Column(name = "owned_card_id", nullable = false, length = 80)
    private String ownedCardId;

    @Column(nullable = false)
    private int position;
}
