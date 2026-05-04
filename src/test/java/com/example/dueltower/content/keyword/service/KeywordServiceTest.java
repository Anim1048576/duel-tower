package com.example.dueltower.content.keyword.service;

import com.example.dueltower.content.keyword.kdb.K004_Summon;
import com.example.dueltower.content.keyword.kdb.K901_SummonHp;
import com.example.dueltower.content.keyword.kdb.K902_SummonAttackPower;
import com.example.dueltower.content.keyword.kdb.K903_SummonHealingPower;
import com.example.dueltower.content.keyword.kdb.K904_Action;
import com.example.dueltower.engine.model.KeywordDefinition;
import com.example.dueltower.engine.model.KeywordRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class KeywordServiceTest {

    private static final Set<String> SUMMON_ATTACHED_KEYWORD_IDS = Set.of(
            K901_SummonHp.ID,
            K902_SummonAttackPower.ID,
            K903_SummonHealingPower.ID,
            K904_Action.ID
    );

    @Autowired
    private KeywordService keywordService;

    @Test
    @DisplayName("4개 인자 KeywordDefinition은 기본적으로 독립 키워드다")
    void fourArgumentKeywordDefinitionDefaultsToStandalone() {
        KeywordDefinition definition = new KeywordDefinition("TEST", "테스트", true, "테스트 키워드");

        assertEquals(KeywordRole.STANDALONE, definition.role());
        assertNull(definition.parentKeywordId());
        assertTrue(definition.standalone());
        assertFalse(definition.attached());
    }

    @Test
    @DisplayName("키워드 기본 목록은 독립 키워드만 반환한다")
    void listReturnsStandaloneKeywordsOnly() {
        Set<String> listedIds = keywordService.list().stream()
                .map(KeywordDefinition::id)
                .collect(Collectors.toSet());

        assertTrue(keywordService.list().stream().allMatch(KeywordDefinition::standalone));
        assertFalse(listedIds.contains(K901_SummonHp.ID));
        assertFalse(listedIds.contains(K902_SummonAttackPower.ID));
        assertFalse(listedIds.contains(K903_SummonHealingPower.ID));
        assertFalse(listedIds.contains(K904_Action.ID));
    }

    @Test
    @DisplayName("소환 부속 키워드는 전체 목록과 부속 조회에서 접근 가능하다")
    void summonAttachedKeywordsRemainAccessibleFromExplicitLookups() {
        Set<String> allIds = keywordService.listAll().stream()
                .map(KeywordDefinition::id)
                .collect(Collectors.toSet());
        Set<String> attachedIds = keywordService.listAttachedTo(K004_Summon.ID).stream()
                .map(KeywordDefinition::id)
                .collect(Collectors.toSet());

        assertTrue(allIds.containsAll(SUMMON_ATTACHED_KEYWORD_IDS));
        assertEquals(SUMMON_ATTACHED_KEYWORD_IDS, attachedIds);

        KeywordDefinition hp = keywordService.get(K901_SummonHp.ID);
        assertNotNull(hp);
        assertEquals(KeywordRole.ATTACHED, hp.role());
        assertEquals(K004_Summon.ID, hp.parentKeywordId());
    }
}
