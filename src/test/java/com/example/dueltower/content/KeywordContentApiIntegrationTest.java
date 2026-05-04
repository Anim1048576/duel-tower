package com.example.dueltower.content;

import com.example.dueltower.content.keyword.kdb.K004_Summon;
import com.example.dueltower.content.keyword.kdb.K901_SummonHp;
import com.example.dueltower.content.keyword.kdb.K902_SummonAttackPower;
import com.example.dueltower.content.keyword.kdb.K903_SummonHealingPower;
import com.example.dueltower.content.keyword.kdb.K904_Action;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class KeywordContentApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("기본 키워드 목록 API는 소환 부속 키워드를 제외한다")
    void keywordListExcludesSummonAttachedKeywords() throws Exception {
        mockMvc.perform(get("/api/content/keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", not(hasItems(
                        K901_SummonHp.ID,
                        K902_SummonAttackPower.ID,
                        K903_SummonHealingPower.ID,
                        K904_Action.ID
                ))));
    }

    @Test
    @DisplayName("전체 키워드 목록 API는 소환 부속 키워드를 포함한다")
    void keywordAllListIncludesSummonAttachedKeywords() throws Exception {
        mockMvc.perform(get("/api/content/keywords/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItems(
                        K901_SummonHp.ID,
                        K902_SummonAttackPower.ID,
                        K903_SummonHealingPower.ID,
                        K904_Action.ID
                )));
    }

    @Test
    @DisplayName("소환 키워드의 부속 키워드 API는 체력 공격력 치유력 액션을 반환한다")
    void keywordAttachedListReturnsSummonAttachedKeywords() throws Exception {
        mockMvc.perform(get("/api/content/keywords/{id}/attached", K004_Summon.ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItems(
                        K901_SummonHp.ID,
                        K902_SummonAttackPower.ID,
                        K903_SummonHealingPower.ID,
                        K904_Action.ID
                )))
                .andExpect(jsonPath("$[0].role").value("ATTACHED"))
                .andExpect(jsonPath("$[0].parentKeywordId").value(K004_Summon.ID));
    }

    @Test
    @DisplayName("소환 부속 키워드는 ID 직접 조회로 접근 가능하다")
    void attachedKeywordDetailStillWorks() throws Exception {
        mockMvc.perform(get("/api/content/keywords/{id}", K901_SummonHp.ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(K901_SummonHp.ID))
                .andExpect(jsonPath("$.role").value("ATTACHED"))
                .andExpect(jsonPath("$.parentKeywordId").value(K004_Summon.ID));
    }
}
