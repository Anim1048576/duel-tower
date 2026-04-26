package com.example.dueltower.character.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CharacterProfileControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("character create는 currentSkillDeck 직접 쓰기를 거부한다")
    void createRejectsCurrentSkillDeckWrite() throws Exception {
        MockHttpSession session = signUpAndLogin("characterCreateBlocked");

        mockMvc.perform(post("/api/content/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "blocked",
                                  "currentSkillDeck": null
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("currentSkillDeck cannot be written through character create/update")));
    }

    @Test
    @DisplayName("character update는 currentSkillDeck 직접 쓰기를 거부한다")
    void updateRejectsCurrentSkillDeckWrite() throws Exception {
        MockHttpSession session = signUpAndLogin("characterUpdateBlocked");

        mockMvc.perform(put("/api/content/characters/{id}", 1)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "blocked",
                                  "currentSkillDeck": ["C001"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("use the dedicated current skill deck API")));
    }

    @Test
    @DisplayName("character create without currentSkillDeck stores normal fields and does not expose raw currentSkillDeck")
    void createWithoutCurrentSkillDeckStoresOtherFields() throws Exception {
        MockHttpSession session = signUpAndLogin("characterCreateAllowed");

        mockMvc.perform(post("/api/content/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCharacterBody("created-name", "[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("created-name"))
                .andExpect(jsonPath("$.hiddenTraitIds").isEmpty())
                .andExpect(jsonPath("$.ownedCards").value("[]"))
                .andExpect(jsonPath("$.exCard").value("{}"))
                .andExpect(jsonPath("$.currentSkillDeck").doesNotExist())
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds").isEmpty());
    }

    @Test
    @DisplayName("character create/update/read responses preserve hiddenTraitIds")
    void createUpdateReadPreservesHiddenTraitIds() throws Exception {
        MockHttpSession session = signUpAndLogin("characterHiddenTraitsPreserved");

        MvcResult createResult = mockMvc.perform(post("/api/content/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCharacterBody("hidden-before", "[]", "[\"HT001\", \"HT002\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hiddenTraitIds.length()").value(2))
                .andExpect(jsonPath("$.hiddenTraitIds[0]").value("HT001"))
                .andExpect(jsonPath("$.hiddenTraitIds[1]").value("HT002"))
                .andReturn();
        String characterId = extractJsonNumber(createResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(put("/api/content/characters/{id}", characterId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCharacterBody("hidden-after", "[]", "[\"HT001\", \"HT002\"]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("hidden-after"))
                .andExpect(jsonPath("$.hiddenTraitIds.length()").value(2))
                .andExpect(jsonPath("$.hiddenTraitIds[0]").value("HT001"))
                .andExpect(jsonPath("$.hiddenTraitIds[1]").value("HT002"));

        mockMvc.perform(get("/api/content/characters/{id}", characterId)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("hidden-after"))
                .andExpect(jsonPath("$.hiddenTraitIds.length()").value(2))
                .andExpect(jsonPath("$.hiddenTraitIds[0]").value("HT001"))
                .andExpect(jsonPath("$.hiddenTraitIds[1]").value("HT002"));
    }

    @Test
    @DisplayName("character update without currentSkillDeck stores normal fields")
    void updateWithoutCurrentSkillDeckStoresOtherFields() throws Exception {
        MockHttpSession session = signUpAndLogin("characterUpdateAllowed");
        MvcResult createResult = mockMvc.perform(post("/api/content/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCharacterBody("before-name", "[]")))
                .andExpect(status().isOk())
                .andReturn();
        String characterId = extractJsonNumber(createResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(put("/api/content/characters/{id}", characterId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCharacterBody("after-name", "[{\\\"cardId\\\":\\\"C001\\\"}]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("after-name"))
                .andExpect(jsonPath("$.hiddenTraitIds").isEmpty())
                .andExpect(jsonPath("$.ownedCards").isString())
                .andExpect(jsonPath("$.currentSkillDeck").doesNotExist())
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds").isEmpty());

        mockMvc.perform(get("/api/content/characters/{id}", characterId)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("after-name"))
                .andExpect(jsonPath("$.hiddenTraitIds").isEmpty())
                .andExpect(jsonPath("$.ownedCards").isString())
                .andExpect(jsonPath("$.currentSkillDeck").doesNotExist())
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds").isEmpty());
    }

    private String validCharacterBody(String name, String ownedCards) {
        return validCharacterBody(name, ownedCards, "[]");
    }

    private String validCharacterBody(String name, String ownedCards, String hiddenTraitIdsJson) {
        return """
                {
                  "name": "%s",
                  "gender": "MALE",
                  "age": 20,
                  "wish": "wish",
                  "disposition": "\\uC911\\uB9BD/\\uC911\\uC6A9",
                  "oneLiner": "oneLiner",
                  "story": "story",
                  "physical": 5,
                  "technique": 5,
                  "sense": 5,
                  "willpower": 5,
                  "trait1": "trait1",
                  "trait2": "trait2",
                  "hiddenTraitIds": %s,
                  "ownedCards": "%s",
                  "exCard": "{}"
                }
                """.formatted(name, hiddenTraitIdsJson, ownedCards);
    }

    private String extractJsonNumber(String json, String key) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"" + java.util.regex.Pattern.quote(key) + "\"\\s*:\\s*(\\d+)")
                .matcher(json);
        org.junit.jupiter.api.Assertions.assertTrue(matcher.find(), "JSON number field not found: " + key);
        return matcher.group(1);
    }

    private MockHttpSession signUpAndLogin(String username) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"password123"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk());

        return (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username":"%s",
                                  "password":"password123"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);
    }
}
