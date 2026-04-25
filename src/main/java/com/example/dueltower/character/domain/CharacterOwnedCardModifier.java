package com.example.dueltower.character.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Table(name = "character_owned_card_modifiers")
public class CharacterOwnedCardModifier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.PROTECTED)
    private Long id;

    @Column(name = "owned_card_id", nullable = false, length = 80)
    private String ownedCardId;

    @Column(name = "modifier_id", nullable = false, length = 80)
    private String modifierId;

    @Column(name = "modifier_value", nullable = false)
    private int value;
}
