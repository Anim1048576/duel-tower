package com.example.dueltower.character.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;

/**
 * Character basic profile root.
 *
 * <p>Owned cards, equipped current skill deck, equipped EX card, and hidden traits are normalized into
 * CharacterOwnedCard, CharacterCurrentSkillDeckEntry, CharacterExLoadout, and CharacterHiddenTrait.
 * CharacterProfile keeps only identity, profile text, stats, and traits.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "character_profiles")
public class CharacterProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(AccessLevel.PROTECTED)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CharacterGender gender;

    @Column
    private Integer age;

    @Column(nullable = false, length = 255)
    private String wish;

    @Column(nullable = false, length = 100)
    private String disposition;

    @Column(nullable = false, length = 255)
    private String oneLiner;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String story;

    /** 생활 능력치 */
    @Column(nullable = false)
    private int physical;

    @Column(nullable = false)
    private int technique;

    @Column(nullable = false)
    private int sense;

    @Column(nullable = false)
    private int willpower;

    /** 캐릭터 특성 0~2개 */
    @Column(length = 100)
    private String trait1;

    @Column(length = 100)
    private String trait2;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Timestamp createDate;

    @UpdateTimestamp
    @Column(nullable = false)
    private Timestamp updateDate;
}
