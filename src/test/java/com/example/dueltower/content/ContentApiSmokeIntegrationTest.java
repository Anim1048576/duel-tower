package com.example.dueltower.content;

import com.example.dueltower.content.keyword.kdb.K003_Installed;
import com.example.dueltower.content.meta.ContentOwnerIds;
import com.example.dueltower.content.status.sdb.player.tig.Tig202_Status;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentApiSmokeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("카드 엔드포인트는 정상 응답과 비어 있지 않은 payload를 반환한다")
    void cardsEndpointShouldReturnOkAndNonEmptyPayload() throws Exception {
        mockMvc.perform(get("/api/content/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$[0].contentOwner").exists());
    }

    @Test
    @DisplayName("카드 상세 엔드포인트는 알려진 ID에 대해 정상 응답을 반환한다")
    void cardDetailEndpointShouldReturnOkForKnownId() throws Exception {
        mockMvc.perform(get("/api/content/cards/C001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id.value").value("C001"))
                .andExpect(jsonPath("$.contentOwner").value(ContentOwnerIds.COMMON));
    }

    @Test
    @DisplayName("카드 상세 엔드포인트는 알 수 없는 ID에 대해 NOT_FOUND를 반환한다")
    void cardDetailEndpointShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/cards/C999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("카드 엔드포인트는 q/type/keyword 필터를 지원한다")
    void cardsEndpointShouldSupportQTypeAndKeywordFilters() throws Exception {
        mockMvc.perform(get("/api/content/cards")
                        .param("q", "기본")
                        .param("type", "SKILL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));

        mockMvc.perform(get("/api/content/cards")
                        .param("keywordId", "__NO_SUCH_KEYWORD__"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(get("/api/content/cards")
                        .param("q", "Tig008_Card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$[0].contentOwner").value(ContentOwnerIds.TIG));
    }

    @Test
    @DisplayName("카드 목록 API는 무명 카드를 노출한다")
    void cardsEndpointShouldExposeNamelessCards() throws Exception {
        mockMvc.perform(get("/api/content/cards")
                        .param("q", "Nameless001_Card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$[0].id.value").value("Nameless001_Card"))
                .andExpect(jsonPath("$[0].contentOwner").value(ContentOwnerIds.NAMELESS));
    }

    @Test
    @DisplayName("키워드 엔드포인트는 정상 응답과 비어 있지 않은 payload를 반환한다")
    void keywordsEndpointShouldReturnOkAndNonEmptyPayload() throws Exception {
        mockMvc.perform(get("/api/content/keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$[0].contentOwner").exists());
    }

    @Test
    @DisplayName("키워드 상세 엔드포인트는 알려진 ID에 대해 정상 응답을 반환한다")
    void keywordDetailEndpointShouldReturnOkForKnownId() throws Exception {
        mockMvc.perform(get("/api/content/keywords/{id}", K003_Installed.ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(K003_Installed.ID))
                .andExpect(jsonPath("$.contentOwner").value(ContentOwnerIds.COMMON));
    }

    @Test
    @DisplayName("키워드 상세 엔드포인트는 알 수 없는 ID에 대해 NOT_FOUND를 반환한다")
    void keywordDetailEndpointShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/keywords/K999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("패시브 엔드포인트는 정상 응답과 비어 있지 않은 payload를 반환한다")
    void passivesEndpointShouldReturnOkAndNonEmptyPayload() throws Exception {
        mockMvc.perform(get("/api/content/passives"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$[0].contentOwner").exists());
    }

    @Test
    @DisplayName("패시브 상세 엔드포인트는 알려진 ID에 대해 정상 응답을 반환한다")
    void passiveDetailEndpointShouldReturnOkForKnownId() throws Exception {
        mockMvc.perform(get("/api/content/passives/P001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("P001"))
                .andExpect(jsonPath("$.contentOwner").value(ContentOwnerIds.COMMON));
    }

    @Test
    @DisplayName("패시브 상세 엔드포인트는 알 수 없는 ID에 대해 NOT_FOUND를 반환한다")
    void passiveDetailEndpointShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/passives/P999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("상태 엔드포인트는 정상 응답과 비어 있지 않은 payload를 반환한다")
    void statusesEndpointShouldReturnOkAndNonEmptyPayload() throws Exception {
        mockMvc.perform(get("/api/content/statuses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)))
                .andExpect(jsonPath("$[0].contentOwner").exists());
    }

    @Test
    @DisplayName("상태 상세 엔드포인트는 알려진 ID에 대해 정상 응답을 반환한다")
    void statusDetailEndpointShouldReturnOkForKnownId() throws Exception {
        mockMvc.perform(get("/api/content/statuses/SHIELD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("SHIELD"))
                .andExpect(jsonPath("$.contentOwner").value(ContentOwnerIds.COMMON));
    }

    @Test
    @DisplayName("content API exposes contentOwner metadata for Tig-only status detail")
    void statusDetailEndpointShouldReturnContentOwnerForTigStatus() throws Exception {
        mockMvc.perform(get("/api/content/statuses/{id}", Tig202_Status.ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Tig202_Status.ID))
                .andExpect(jsonPath("$.contentOwner").value(ContentOwnerIds.TIG));
    }

    @Test
    @DisplayName("상태 상세 엔드포인트는 알 수 없는 ID에 대해 NOT_FOUND를 반환한다")
    void statusDetailEndpointShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/statuses/S999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("덱 엔드포인트는 정상 응답을 반환한다")
    void decksEndpointShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/content/decks"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("캐릭터 엔드포인트는 정상 응답을 반환한다")
    void charactersEndpointShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/content/characters"))
                .andExpect(status().isOk());
    }
}
