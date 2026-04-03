package com.example.dueltower.content;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.keyword.service.KeywordService;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.content.status.service.StatusService;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.KeywordDefinition;
import com.example.dueltower.engine.model.PassiveDefinition;
import com.example.dueltower.engine.model.StatusDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentDetailApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CardService cardService;

    @Autowired
    private PassiveService passiveService;

    @Autowired
    private StatusService statusService;

    @Autowired
    private KeywordService keywordService;

    @Test
    void cardDetailShouldBeAccessibleWithoutLoginAndContainCoreFields() throws Exception {
        CardDefinition knownCard = cardService.list().stream().findFirst().orElseThrow();
        String cardId = knownCard.id().value();

        mockMvc.perform(get("/api/content/cards/{id}", cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id.value").value(cardId))
                .andExpect(jsonPath("$.name", not(emptyOrNullString())))
                .andExpect(jsonPath("$.type").isString())
                .andExpect(jsonPath("$.cost").isNumber())
                .andExpect(jsonPath("$.keywords").isMap())
                .andExpect(jsonPath("$.resolveTo").isString())
                .andExpect(jsonPath("$.token").isBoolean())
                .andExpect(jsonPath("$.description", not(emptyOrNullString())));
    }


    @Test
    void cardDetailShouldAllowTrimmedIdLookup() throws Exception {
        CardDefinition knownCard = cardService.list().stream().findFirst().orElseThrow();
        String padded = " " + knownCard.id().value() + " ";

        mockMvc.perform(get("/api/content/cards/{id}", padded))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id.value").value(knownCard.id().value()));
    }

    @Test
    void cardDetailShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/cards/{id}", "__UNKNOWN_CARD__"))
                .andExpect(status().isNotFound());
    }

    @Test
    void passiveDetailShouldReturnOkForKnownId() throws Exception {
        PassiveDefinition knownPassive = passiveService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/passives/{id}", knownPassive.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(knownPassive.id()));
    }

    @Test
    void passiveDetailShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/passives/{id}", "__UNKNOWN_PASSIVE__"))
                .andExpect(status().isNotFound());
    }

    @Test
    void statusDetailShouldReturnOkForKnownId() throws Exception {
        StatusDefinition knownStatus = statusService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/statuses/{id}", knownStatus.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(knownStatus.id()));
    }

    @Test
    void statusDetailShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/statuses/{id}", "__UNKNOWN_STATUS__"))
                .andExpect(status().isNotFound());
    }

    @Test
    void keywordDetailShouldReturnOkForKnownId() throws Exception {
        KeywordDefinition knownKeyword = keywordService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/keywords/{id}", knownKeyword.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(knownKeyword.id()));
    }

    @Test
    void keywordDetailShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/keywords/{id}", "__UNKNOWN_KEYWORD__"))
                .andExpect(status().isNotFound());
    }

    @Test
    void contentDetailEndpointsAndCardListShouldRemainPublic() throws Exception {
        CardDefinition knownCard = cardService.list().stream().findFirst().orElseThrow();
        PassiveDefinition knownPassive = passiveService.list().stream().findFirst().orElseThrow();
        StatusDefinition knownStatus = statusService.list().stream().findFirst().orElseThrow();
        KeywordDefinition knownKeyword = keywordService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/cards"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/content/cards/{id}", knownCard.id().value()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/content/passives/{id}", knownPassive.id()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/content/statuses/{id}", knownStatus.id()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/content/keywords/{id}", knownKeyword.id()))
                .andExpect(status().isOk());
    }
}
