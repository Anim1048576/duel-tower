package com.example.dueltower.content;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.item.service.ItemService;
import com.example.dueltower.content.keyword.service.KeywordService;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.content.status.service.StatusService;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.ItemDefinition;
import com.example.dueltower.engine.model.KeywordDefinition;
import com.example.dueltower.engine.model.PassiveDefinition;
import com.example.dueltower.engine.model.StatusDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
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

    @Autowired
    private ItemService itemService;

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
                .andExpect(jsonPath("$.description", not(emptyOrNullString())))
                .andExpect(jsonPath("$.playSpec").exists())
                .andExpect(jsonPath("$.playSpec.target").exists())
                .andExpect(jsonPath("$.playSpec.extraRequirements").isArray());
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
    void cardDetailShouldExposePlaySpecMetadataForTigDiscardCards() throws Exception {
        mockMvc.perform(get("/api/content/cards/{id}", "Tig004_Card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playSpec.target.target").value("ENEMY_ONE"))
                .andExpect(jsonPath("$.playSpec.target.requiredSelection").value(true))
                .andExpect(jsonPath("$.playSpec.extraRequirements.length()").value(1));

        mockMvc.perform(get("/api/content/cards/{id}", "Tig005_Card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playSpec.target.target").value("NONE"))
                .andExpect(jsonPath("$.playSpec.target.requiredSelection").value(false))
                .andExpect(jsonPath("$.playSpec.extraRequirements.length()").value(1));

        mockMvc.perform(get("/api/content/cards/{id}", "Tig006_Card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playSpec.target.target").value("NONE"))
                .andExpect(jsonPath("$.playSpec.target.requiredSelection").value(false))
                .andExpect(jsonPath("$.playSpec.extraRequirements.length()").value(1));

        mockMvc.perform(get("/api/content/cards/{id}", "Tig008_Card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playSpec.target.target").value("ENEMY_ONE"))
                .andExpect(jsonPath("$.playSpec.target.requiredSelection").value(true))
                .andExpect(jsonPath("$.playSpec.extraRequirements.length()").value(1));
    }

    @Test
    void cardDetailShouldExposeNonePlaySpecForDefaultCard() throws Exception {
        mockMvc.perform(get("/api/content/cards/{id}", "Tig001_Card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playSpec.target.target").value("NONE"))
                .andExpect(jsonPath("$.playSpec.target.requiredSelection").value(false))
                .andExpect(jsonPath("$.playSpec.extraRequirements.length()").value(0));
    }

    @Test
    void passiveDetailShouldReturnOkForKnownId() throws Exception {
        PassiveDefinition knownPassive = passiveService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/passives/{id}", knownPassive.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(knownPassive.id()))
                .andExpect(jsonPath("$.name", not(emptyOrNullString())))
                .andExpect(jsonPath("$.description", not(emptyOrNullString())));
    }

    @Test
    void passiveDetailShouldAllowTrimmedIdLookup() throws Exception {
        PassiveDefinition knownPassive = passiveService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/passives/{id}", " " + knownPassive.id() + " "))
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
                .andExpect(jsonPath("$.id").value(knownStatus.id()))
                .andExpect(jsonPath("$.name", not(emptyOrNullString())))
                .andExpect(jsonPath("$.description", not(emptyOrNullString())));
    }

    @Test
    void statusDetailShouldAllowTrimmedIdLookup() throws Exception {
        StatusDefinition knownStatus = statusService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/statuses/{id}", " " + knownStatus.id() + " "))
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
                .andExpect(jsonPath("$.id").value(knownKeyword.id()))
                .andExpect(jsonPath("$.name", not(emptyOrNullString())))
                .andExpect(jsonPath("$.description", not(emptyOrNullString())));
    }

    @Test
    void keywordDetailShouldAllowTrimmedIdLookup() throws Exception {
        KeywordDefinition knownKeyword = keywordService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/keywords/{id}", " " + knownKeyword.id() + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(knownKeyword.id()));
    }

    @Test
    void keywordDetailShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/keywords/{id}", "__UNKNOWN_KEYWORD__"))
                .andExpect(status().isNotFound());
    }

    @Test
    void itemDetailShouldReturnOkForKnownId() throws Exception {
        ItemDefinition knownItem = itemService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/items/{id}", knownItem.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(knownItem.id()))
                .andExpect(jsonPath("$.name", not(emptyOrNullString())))
                .andExpect(jsonPath("$.summary", not(emptyOrNullString())))
                .andExpect(jsonPath("$.description", not(emptyOrNullString())))
                .andExpect(jsonPath("$.battleUsable").isBoolean())
                .andExpect(jsonPath("$.tags").isArray());
    }

    @Test
    void itemDetailShouldAllowTrimmedIdLookup() throws Exception {
        ItemDefinition knownItem = itemService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/items/{id}", " " + knownItem.id() + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(knownItem.id()));
    }

    @Test
    void itemDetailShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/items/{id}", "__UNKNOWN_ITEM__"))
                .andExpect(status().isNotFound());
    }

    @Test
    void itemListShouldReturnRegisteredItems() throws Exception {
        mockMvc.perform(get("/api/content/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$[*].id", hasItems("I-1", "I-2", "I-3", "I-4", "I-5")));
    }

    @Test
    void contentDetailEndpointsAndCardListShouldRemainPublic() throws Exception {
        CardDefinition knownCard = cardService.list().stream().findFirst().orElseThrow();
        PassiveDefinition knownPassive = passiveService.list().stream().findFirst().orElseThrow();
        StatusDefinition knownStatus = statusService.list().stream().findFirst().orElseThrow();
        KeywordDefinition knownKeyword = keywordService.list().stream().findFirst().orElseThrow();
        ItemDefinition knownItem = itemService.list().stream().findFirst().orElseThrow();

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
        mockMvc.perform(get("/api/content/items"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/content/items/{id}", knownItem.id()))
                .andExpect(status().isOk());
    }
}
