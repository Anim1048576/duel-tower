package com.example.dueltower.character.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;

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

    /** 보유 카드 현황(JSON 문자열) */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String ownedCards;

    /** 현재 스킬 덱 구성(JSON 문자열) */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String currentSkillDeck;

    /** EX 카드 정보(JSON 문자열) */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String exCard;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Timestamp createDate;

    @UpdateTimestamp
    @Column(nullable = false)
    private Timestamp updateDate;
}
