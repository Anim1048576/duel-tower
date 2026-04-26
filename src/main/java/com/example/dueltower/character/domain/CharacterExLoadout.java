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
 * Currently equipped EX card for a character.
 *
 * <p>This is the persistence source of truth for CharacterProfileResponse.exCard compatibility JSON.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "character_ex_loadouts")
public class CharacterExLoadout {

    @Id
    @Column(name = "character_id", nullable = false)
    private Long characterId;

    @Column(name = "ex_card_id", nullable = false, length = 40)
    private String exCardId;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Timestamp createDate;

    @UpdateTimestamp
    @Column(nullable = false)
    private Timestamp updateDate;
}
