package com.example.dueltower.character.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;

/**
 * A concrete card copy owned by a character.
 *
 * <p>This is the persistence source of truth for CharacterProfileResponse.ownedCards compatibility JSON.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "character_owned_cards")
public class CharacterOwnedCard {

    @Id
    @Column(name = "owned_card_id", nullable = false, length = 80)
    private String ownedCardId;

    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Column(name = "card_id", nullable = false, length = 40)
    private String cardId;

    @Column(nullable = false)
    private boolean strengthened;

    @Column(nullable = false)
    private boolean weakened;

    @Column(name = "locked_in_deck", nullable = false)
    private boolean lockedInDeck;

    @Column(nullable = false)
    private boolean forgettable;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Timestamp createDate;

    @UpdateTimestamp
    @Column(nullable = false)
    private Timestamp updateDate;
}
