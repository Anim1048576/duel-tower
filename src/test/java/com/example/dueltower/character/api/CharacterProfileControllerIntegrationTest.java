package com.example.dueltower.character.api;

import com.example.dueltower.character.repository.CharacterCurrentSkillDeckEntryRepository;
import com.example.dueltower.character.repository.CharacterExLoadoutRepository;
import com.example.dueltower.character.repository.CharacterOwnedCardModifierRepository;
import com.example.dueltower.character.repository.CharacterOwnedCardRepository;
import com.example.dueltower.character.service.CharacterLoadoutService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Autowired
    private CharacterLoadoutService characterLoadoutService;

    @Autowired
    private CharacterCurrentSkillDeckEntryRepository currentSkillDeckEntryRepository;

    @Autowired
    private CharacterExLoadoutRepository exLoadoutRepository;

    @Autowired
    private CharacterOwnedCardRepository ownedCardRepository;

    @Autowired
    private CharacterOwnedCardModifierRepository ownedCardModifierRepository;

    @Test
    @DisplayName("character create rejects direct currentSkillDeck writes")
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
    @DisplayName("character update rejects direct currentSkillDeck writes")
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
    @DisplayName("character create returns profile fields plus loadout preview without raw currentSkillDeck")
    void createWithoutCurrentSkillDeckStoresOtherFields() throws Exception {
        MockHttpSession session = signUpAndLogin("characterCreateAllowed");

        mockMvc.perform(post("/api/content/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCharacterBody("created-name", "[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("created-name"))
                .andExpect(jsonPath("$.hiddenTraitIds").isEmpty())
                .andExpect(jsonPath("$.ownedCardList").isArray())
                .andExpect(jsonPath("$.ownedCardList").isEmpty())
                .andExpect(jsonPath("$.exCardId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.currentSkillDeck").doesNotExist())
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds").isEmpty());
    }

    @Test
    @DisplayName("character create rejects unknown exCard id")
    void createRejectsUnknownExCardId() throws Exception {
        MockHttpSession session = signUpAndLogin("characterCreateUnknownEx");

        mockMvc.perform(post("/api/content/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCharacterBody("invalid-ex-create", "[]", "[]", "{\\\"id\\\":\\\"NO_SUCH_CARD\\\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("invalid exCardId: NO_SUCH_CARD")));
    }

    @Test
    @DisplayName("character create accepts structured ownedCardList without legacy ownedCards")
    void createReturnsStructuredOwnedCardListWithModifiersWithoutExCard() throws Exception {
        MockHttpSession session = signUpAndLogin("characterCreateStructuredOwned");

        mockMvc.perform(post("/api/content/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(characterBodyWithLoadoutFields(
                                "structured-owned-create",
                                """
                                          "ownedCardList": [
                                            {
                                              "ownedCardId": "oc-1",
                                              "cardId": "C001",
                                              "modifiers": [
                                                { "modifierId": "STRENGTHENED", "value": 1 }
                                              ],
                                              "strengthened": true,
                                              "weakened": false,
                                              "lockedInDeck": false,
                                              "forgettable": true
                                            }
                                          ],
                                          "exCard": "{}"
                                        """
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownedCardList").isArray())
                .andExpect(jsonPath("$.ownedCardList[0].ownedCardId").value("oc-1"))
                .andExpect(jsonPath("$.ownedCardList[0].cardId").value("C001"))
                .andExpect(jsonPath("$.ownedCardList[0].modifiers").isArray())
                .andExpect(jsonPath("$.ownedCardList[0].modifiers[0].modifierId").value("STRENGTHENED"))
                .andExpect(jsonPath("$.ownedCardList[0].modifiers[0].value").value(1))
                .andExpect(jsonPath("$.ownedCardList[0].strengthened").value(org.hamcrest.Matchers.isA(Boolean.class)))
                .andExpect(jsonPath("$.ownedCardList[0].strengthened").value(true))
                .andExpect(jsonPath("$.ownedCardList[0].weakened").value(org.hamcrest.Matchers.isA(Boolean.class)))
                .andExpect(jsonPath("$.ownedCardList[0].weakened").value(false))
                .andExpect(jsonPath("$.ownedCardList[0].lockedInDeck").value(org.hamcrest.Matchers.isA(Boolean.class)))
                .andExpect(jsonPath("$.ownedCardList[0].lockedInDeck").value(false))
                .andExpect(jsonPath("$.ownedCardList[0].forgettable").value(org.hamcrest.Matchers.isA(Boolean.class)))
                .andExpect(jsonPath("$.ownedCardList[0].forgettable").value(true))
                .andExpect(jsonPath("$.exCardId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.currentSkillDeck").doesNotExist())
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds").isArray());
    }

    @Test
    @DisplayName("character create accepts exCardId without legacy exCard")
    void createReturnsExCardIdWhenExCardIsEquipped() throws Exception {
        MockHttpSession session = signUpAndLogin("characterCreateStructuredEx");

        mockMvc.perform(post("/api/content/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(characterBodyWithLoadoutFields(
                                "structured-ex-create",
                                """
                                          "ownedCards": "[]",
                                          "exCardId": "EX901"
                                        """
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownedCardList").isArray())
                .andExpect(jsonPath("$.exCardId").value("EX901"))
                .andExpect(jsonPath("$.currentSkillDeck").doesNotExist())
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds").isArray());
    }

    @Test
    @DisplayName("structured ownedCardList takes precedence over legacy ownedCards")
    void structuredOwnedCardListTakesPrecedenceOverLegacyOwnedCards() throws Exception {
        MockHttpSession session = signUpAndLogin("characterStructuredOwnedPrecedence");

        mockMvc.perform(post("/api/content/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(characterBodyWithLoadoutFields(
                                "structured-owned-precedence",
                                """
                                          "ownedCards": "[{\\"cardId\\":\\"C002\\"}]",
                                          "ownedCardList": [
                                            {
                                              "ownedCardId": "oc-precedence",
                                              "cardId": "C001",
                                              "modifiers": [],
                                              "strengthened": false,
                                              "weakened": false,
                                              "lockedInDeck": false,
                                              "forgettable": true
                                            }
                                          ],
                                          "exCard": "{}"
                                        """
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownedCardList[0].ownedCardId").value("oc-precedence"))
                .andExpect(jsonPath("$.ownedCardList[0].cardId").value("C001"));
    }

    @Test
    @DisplayName("exCardId takes precedence over legacy exCard")
    void exCardIdTakesPrecedenceOverLegacyExCard() throws Exception {
        MockHttpSession session = signUpAndLogin("characterExPrecedence");

        mockMvc.perform(post("/api/content/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(characterBodyWithLoadoutFields(
                                "structured-ex-precedence",
                                """
                                          "ownedCards": "[]",
                                          "exCard": "{\\"id\\":\\"C001\\"}",
                                          "exCardId": "EX901"
                                        """
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exCardId").value("EX901"));
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
    @DisplayName("character update returns profile fields plus loadout preview without raw currentSkillDeck")
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
                .andExpect(jsonPath("$.ownedCardList").isArray())
                .andExpect(jsonPath("$.ownedCardList[0].cardId").value("C001"))
                .andExpect(jsonPath("$.currentSkillDeck").doesNotExist())
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds").isEmpty());

        mockMvc.perform(get("/api/content/characters/{id}", characterId)
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("after-name"))
                .andExpect(jsonPath("$.hiddenTraitIds").isEmpty())
                .andExpect(jsonPath("$.ownedCardList").isArray())
                .andExpect(jsonPath("$.ownedCardList[0].cardId").value("C001"))
                .andExpect(jsonPath("$.currentSkillDeck").doesNotExist())
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds").isEmpty());
    }

    @Test
    @DisplayName("character update rejects non-EX exCard id")
    void updateRejectsNonExCardId() throws Exception {
        MockHttpSession session = signUpAndLogin("characterUpdateNonEx");
        MvcResult createResult = mockMvc.perform(post("/api/content/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCharacterBody("before-invalid-ex", "[]")))
                .andExpect(status().isOk())
                .andReturn();
        String characterId = extractJsonNumber(createResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(put("/api/content/characters/{id}", characterId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCharacterBody("after-invalid-ex", "[]", "[]", "{\\\"id\\\":\\\"C001\\\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("exCardId must reference an EX card: C001")));
    }

    @Test
    @DisplayName("character update accepts structured ownedCardList")
    void updateAcceptsStructuredOwnedCardList() throws Exception {
        MockHttpSession session = signUpAndLogin("characterUpdateStructuredOwned");
        MvcResult createResult = mockMvc.perform(post("/api/content/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCharacterBody("before-structured-owned-update", "[]")))
                .andExpect(status().isOk())
                .andReturn();
        String characterId = extractJsonNumber(createResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(put("/api/content/characters/{id}", characterId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(characterBodyWithLoadoutFields(
                                "after-structured-owned-update",
                                """
                                          "ownedCardList": [
                                            {
                                              "ownedCardId": "oc-update-1",
                                              "cardId": "C001",
                                              "modifiers": [],
                                              "strengthened": false,
                                              "weakened": false,
                                              "lockedInDeck": false,
                                              "forgettable": true
                                            }
                                          ],
                                          "exCard": "{}"
                                        """
                )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownedCardList[0].ownedCardId").value("oc-update-1"))
                .andExpect(jsonPath("$.ownedCardList[0].cardId").value("C001"))
                .andExpect(jsonPath("$.exCardId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.currentSkillDeck").doesNotExist())
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds").isEmpty());
    }

    @Test
    @DisplayName("character update clears current skill deck before replacing owned cards")
    void updateClearsCurrentSkillDeckBeforeReplacingOwnedCards() throws Exception {
        MockHttpSession session = signUpAndLogin("characterUpdateOwnedCardFkSafe");
        MvcResult createResult = mockMvc.perform(post("/api/content/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(characterBodyWithLoadoutFields(
                                "before-owned-card-fk-safe-update",
                                """
                                          "ownedCardList": [
                                            {
                                              "ownedCardId": "oc-fk-1",
                                              "cardId": "C001",
                                              "modifiers": [],
                                              "strengthened": false,
                                              "weakened": false,
                                              "lockedInDeck": false,
                                              "forgettable": true
                                            }
                                          ],
                                          "exCard": "{}"
                                        """
                        )))
                .andExpect(status().isOk())
                .andReturn();
        Long characterId = Long.parseLong(extractJsonNumber(createResult.getResponse().getContentAsString(), "id"));
        characterLoadoutService.replaceCurrentSkillDeckFromOwnedCardIds(characterId, java.util.List.of("oc-fk-1"));

        mockMvc.perform(put("/api/content/characters/{id}", characterId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(characterBodyWithLoadoutFields(
                                "after-owned-card-fk-safe-update",
                                """
                                          "ownedCardList": [
                                            {
                                              "ownedCardId": "oc-fk-2",
                                              "cardId": "C002",
                                              "modifiers": [],
                                              "strengthened": false,
                                              "weakened": false,
                                              "lockedInDeck": false,
                                              "forgettable": true
                                            }
                                          ],
                                          "exCard": "{}"
                                        """
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentSkillDeckPreviewCardIds").isEmpty());

        assertTrue(currentSkillDeckEntryRepository.findByCharacterId(characterId).isEmpty());
        java.util.List<String> ownedCardIds = ownedCardRepository.findByCharacterId(characterId).stream()
                .map(com.example.dueltower.character.domain.CharacterOwnedCard::getOwnedCardId)
                .toList();
        assertEquals(java.util.List.of("oc-fk-2"), ownedCardIds);
    }

    @Test
    @DisplayName("character delete removes owned cards and modifiers")
    void deleteRemovesOwnedCardsAndModifiers() throws Exception {
        MockHttpSession session = signUpAndLogin("characterDeleteOwnedCards");
        MvcResult createResult = mockMvc.perform(post("/api/content/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(characterBodyWithLoadoutFields(
                                "delete-owned-cards",
                                """
                                          "ownedCardList": [
                                            {
                                              "ownedCardId": "oc-delete-1",
                                              "cardId": "C001",
                                              "modifiers": [
                                                { "modifierId": "STRENGTHENED", "value": 1 }
                                              ],
                                              "strengthened": false,
                                              "weakened": false,
                                              "lockedInDeck": false,
                                              "forgettable": true
                                            }
                                          ],
                                          "exCardId": ""
                                        """
                        )))
                .andExpect(status().isOk())
                .andReturn();
        String characterId = extractJsonNumber(createResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(delete("/api/content/characters/{id}", characterId)
                        .session(session))
                .andExpect(status().isOk());

        assertTrue(ownedCardRepository.findByCharacterId(Long.parseLong(characterId)).isEmpty());
        assertTrue(ownedCardModifierRepository.findByOwnedCardId("oc-delete-1").isEmpty());
    }

    @Test
    @DisplayName("character delete removes ex loadout and current skill deck entries")
    void deleteRemovesExLoadoutAndCurrentSkillDeckEntries() throws Exception {
        MockHttpSession session = signUpAndLogin("characterDeleteLoadout");
        MvcResult createResult = mockMvc.perform(post("/api/content/characters")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(characterBodyWithLoadoutFields(
                                "delete-loadout",
                                """
                                          "ownedCardList": [
                                            {
                                              "ownedCardId": "oc-delete-loadout-1",
                                              "cardId": "C001",
                                              "modifiers": [],
                                              "strengthened": false,
                                              "weakened": false,
                                              "lockedInDeck": false,
                                              "forgettable": true
                                            }
                                          ],
                                          "exCardId": "EX901"
                                        """
                        )))
                .andExpect(status().isOk())
                .andReturn();
        Long characterId = Long.parseLong(extractJsonNumber(createResult.getResponse().getContentAsString(), "id"));
        characterLoadoutService.replaceCurrentSkillDeckFromOwnedCardIds(characterId, java.util.List.of("oc-delete-loadout-1"));

        mockMvc.perform(delete("/api/content/characters/{id}", characterId)
                        .session(session))
                .andExpect(status().isOk());

        assertTrue(exLoadoutRepository.findByCharacterId(characterId).isEmpty());
        assertTrue(currentSkillDeckEntryRepository.findByCharacterId(characterId).isEmpty());
    }

    private String validCharacterBody(String name, String ownedCards) {
        return validCharacterBody(name, ownedCards, "[]");
    }

    private String validCharacterBody(String name, String ownedCards, String hiddenTraitIdsJson) {
        return validCharacterBody(name, ownedCards, hiddenTraitIdsJson, "{}");
    }

    private String validCharacterBody(String name, String ownedCards, String hiddenTraitIdsJson, String exCard) {
        return characterBodyWithLoadoutFields(name, hiddenTraitIdsJson, """
                  "ownedCards": "%s",
                  "exCard": "%s"
                """.formatted(ownedCards, exCard));
    }

    private String characterBodyWithLoadoutFields(String name, String loadoutFields) {
        return characterBodyWithLoadoutFields(name, "[]", loadoutFields);
    }

    private String characterBodyWithLoadoutFields(String name, String hiddenTraitIdsJson, String loadoutFields) {
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
                %s
                }
                """.formatted(name, hiddenTraitIdsJson, loadoutFields);
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
