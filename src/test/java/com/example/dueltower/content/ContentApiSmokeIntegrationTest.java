package com.example.dueltower.content;

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
    void cardsEndpointShouldReturnOkAndNonEmptyPayload() throws Exception {
        mockMvc.perform(get("/api/content/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));
    }

    @Test
    void cardDetailEndpointShouldReturnOkForKnownId() throws Exception {
        mockMvc.perform(get("/api/content/cards/C001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id.value").value("C001"));
    }

    @Test
    void cardDetailEndpointShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/cards/C999"))
                .andExpect(status().isNotFound());
    }

    @Test
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
    }

    @Test
    void keywordsEndpointShouldReturnOkAndNonEmptyPayload() throws Exception {
        mockMvc.perform(get("/api/content/keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));
    }

    @Test
    void keywordDetailEndpointShouldReturnOkForKnownId() throws Exception {
        mockMvc.perform(get("/api/content/keywords/설치"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("설치"));
    }

    @Test
    void keywordDetailEndpointShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/keywords/K999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void passivesEndpointShouldReturnOkAndNonEmptyPayload() throws Exception {
        mockMvc.perform(get("/api/content/passives"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));
    }

    @Test
    void passiveDetailEndpointShouldReturnOkForKnownId() throws Exception {
        mockMvc.perform(get("/api/content/passives/P001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("P001"));
    }

    @Test
    void passiveDetailEndpointShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/passives/P999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusesEndpointShouldReturnOkAndNonEmptyPayload() throws Exception {
        mockMvc.perform(get("/api/content/statuses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));
    }

    @Test
    void statusDetailEndpointShouldReturnOkForKnownId() throws Exception {
        mockMvc.perform(get("/api/content/statuses/SHIELD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("SHIELD"));
    }

    @Test
    void statusDetailEndpointShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/statuses/S999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void decksEndpointShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/content/decks"))
                .andExpect(status().isOk());
    }

    @Test
    void charactersEndpointShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/content/characters"))
                .andExpect(status().isOk());
    }
}
