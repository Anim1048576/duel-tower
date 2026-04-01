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
    void keywordsEndpointShouldReturnOkAndNonEmptyPayload() throws Exception {
        mockMvc.perform(get("/api/content/keywords"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));
    }

    @Test
    void passivesEndpointShouldReturnOkAndNonEmptyPayload() throws Exception {
        mockMvc.perform(get("/api/content/passives"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));
    }

    @Test
    void statusesEndpointShouldReturnOkAndNonEmptyPayload() throws Exception {
        mockMvc.perform(get("/api/content/statuses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThan(0)));
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
