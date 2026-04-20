package com.example.dueltower.character.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.sql.Timestamp;
import java.util.List;

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

    /** 전체 비공개 히든 특성 태그 */
    @Convert(converter = ListStringJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> hiddenTraitIds;

    /** 보유 카드 현황(JSON 문자열) */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String ownedCards;

    /** 현재 스킬 덱 선택 목록(세션 runtime 에서는 ownedCardId 기준으로 정규화해서 사용) */
    @Convert(converter = ListStringJsonConverter.class)
    @Column(columnDefinition = "TEXT")
    private List<String> currentSkillDeck;

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
