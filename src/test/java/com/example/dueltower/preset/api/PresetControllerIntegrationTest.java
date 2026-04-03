package com.example.dueltower.preset.api;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.member.MemberRepository;
import com.example.dueltower.preset.repository.PresetRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PresetControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private CharacterProfileRepository characterProfileRepository;

    @Autowired
    private PresetRepository presetRepository;

    @BeforeEach
    void setUp() {
        presetRepository.deleteAll();
        memberRepository.deleteAll();
        characterProfileRepository.deleteAll();
    }

    @Test
    void presetEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/me/presets"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/me/presets/1"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/me/presets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getPresetsReturnsOnlyMineWhenAuthenticated() throws Exception {
        MockHttpSession owner = signUpAndLogin("owner", "owner@example.com", "password123");
        MockHttpSession other = signUpAndLogin("other", "other@example.com", "password123");
        long characterId = createCharacter();

        long ownerPresetId = createPreset(owner, characterId, "owner-1");
        createPreset(other, characterId, "other-1");

        mockMvc.perform(get("/api/me/presets")
                        .session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(ownerPresetId))
                .andExpect(jsonPath("$[0].ownerUsername").value("owner"));
    }

    @Test
    void getPresetByIdSupportsOwnerAndRejectsForbiddenCases() throws Exception {
        MockHttpSession owner = signUpAndLogin("owner", "owner@example.com", "password123");
        MockHttpSession other = signUpAndLogin("other", "other@example.com", "password123");
        long characterId = createCharacter();

        long ownerPresetId = createPreset(owner, characterId, "owner-1");

        mockMvc.perform(get("/api/me/presets/{presetId}", ownerPresetId)
                        .session(owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ownerPresetId));

        mockMvc.perform(get("/api/me/presets/{presetId}", ownerPresetId)
                        .session(other))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/me/presets/{presetId}", 999999L)
                        .session(owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void ownerCanUpdateAndDeletePreset() throws Exception {
        MockHttpSession session = signUpAndLogin("owner", "owner@example.com", "password123");
        long characterId = createCharacter();
        long presetId = createPreset(session, characterId, "starter");

        mockMvc.perform(put("/api/me/presets/{presetId}", presetId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "starter-v2",
                                  "characterId": %d,
                                  "deckCardIds": ["C001", "C001"],
                                  "exCardId": "EX901",
                                  "passiveIds": ["P001", "P002"]
                                }
                                """.formatted(characterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("starter-v2"))
                .andExpect(jsonPath("$.passiveIds.length()").value(2));

        mockMvc.perform(delete("/api/me/presets/{presetId}", presetId)
                        .session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/me/presets/{presetId}", presetId)
                        .session(session))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotUpdateOrDeleteOtherUsersPreset() throws Exception {
        MockHttpSession owner = signUpAndLogin("owner", "owner@example.com", "password123");
        MockHttpSession other = signUpAndLogin("other", "other@example.com", "password123");
        long characterId = createCharacter();
        long presetId = createPreset(owner, characterId, "owner-only");

        mockMvc.perform(put("/api/me/presets/{presetId}", presetId)
                        .session(other)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "hijack",
                                  "characterId": %d,
                                  "deckCardIds": ["C001"],
                                  "exCardId": "EX901",
                                  "passiveIds": ["P001"]
                                }
                                """.formatted(characterId)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/me/presets/{presetId}", presetId)
                        .session(other))
                .andExpect(status().isNotFound());
    }

    @Test
    void createPresetSuccess() throws Exception {
        MockHttpSession session = signUpAndLogin("owner", "owner@example.com", "password123");
        long characterId = createCharacter();

        mockMvc.perform(post("/api/me/presets")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "starter",
                                  "characterId": %d,
                                  "deckCardIds": ["C001", "C002", "C003"],
                                  "exCardId": "EX901",
                                  "passiveIds": ["P001"]
                                }
                                """.formatted(characterId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerUsername").value("owner"))
                .andExpect(jsonPath("$.name").value("starter"))
                .andExpect(jsonPath("$.deckCardIds.length()").value(3));
    }

    @Test
    void createFailsWhenNameMissing() throws Exception {
        MockHttpSession session = signUpAndLogin("owner", "owner@example.com", "password123");
        long characterId = createCharacter();

        mockMvc.perform(post("/api/me/presets")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "characterId": %d,
                                  "deckCardIds": ["C001"],
                                  "exCardId": "EX901",
                                  "passiveIds": ["P001"]
                                }
                                """.formatted(characterId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFailsWhenCharacterIdInvalid() throws Exception {
        MockHttpSession session = signUpAndLogin("owner", "owner@example.com", "password123");

        mockMvc.perform(post("/api/me/presets")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "bad-character",
                                  "characterId": 999999,
                                  "deckCardIds": ["C001"],
                                  "exCardId": "EX901",
                                  "passiveIds": ["P001"]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFailsWhenExCardIdInvalid() throws Exception {
        MockHttpSession session = signUpAndLogin("owner", "owner@example.com", "password123");
        long characterId = createCharacter();

        mockMvc.perform(post("/api/me/presets")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "bad-ex",
                                  "characterId": %d,
                                  "deckCardIds": ["C001"],
                                  "exCardId": "UNKNOWN_EX",
                                  "passiveIds": ["P001"]
                                }
                                """.formatted(characterId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFailsWhenDeckCardIdsInvalid() throws Exception {
        MockHttpSession session = signUpAndLogin("owner", "owner@example.com", "password123");
        long characterId = createCharacter();

        mockMvc.perform(post("/api/me/presets")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "bad-card",
                                  "characterId": %d,
                                  "deckCardIds": ["UNKNOWN_CARD"],
                                  "exCardId": "EX901",
                                  "passiveIds": ["P001"]
                                }
                                """.formatted(characterId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFailsWhenPassiveIdsInvalid() throws Exception {
        MockHttpSession session = signUpAndLogin("owner", "owner@example.com", "password123");
        long characterId = createCharacter();

        mockMvc.perform(post("/api/me/presets")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "bad-passive",
                                  "characterId": %d,
                                  "deckCardIds": ["C001"],
                                  "exCardId": "EX901",
                                  "passiveIds": ["UNKNOWN_PASSIVE"]
                                }
                                """.formatted(characterId)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateFailsWhenReferenceDataInvalid() throws Exception {
        MockHttpSession session = signUpAndLogin("owner", "owner@example.com", "password123");
        long characterId = createCharacter();
        long presetId = createPreset(session, characterId, "owner-only");

        mockMvc.perform(put("/api/me/presets/{presetId}", presetId)
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "bad-update",
                                  "characterId": %d,
                                  "deckCardIds": ["C001"],
                                  "exCardId": "UNKNOWN_EX",
                                  "passiveIds": ["P001"]
                                }
                                """.formatted(characterId)))
                .andExpect(status().isBadRequest());
    }

    private long createCharacter() {
        CharacterProfile profile = characterProfileRepository.save(CharacterProfile.builder()
                .name("테스트 캐릭터")
                .gender(CharacterGender.OTHER)
                .age(20)
                .wish("테스트")
                .disposition("질서/선")
                .oneLiner("안녕하세요")
                .story("preset 테스트")
                .physical(10)
                .technique(10)
                .sense(10)
                .willpower(10)
                .trait1("P001")
                .trait2(null)
                .ownedCards("[\"C001\",\"C002\",\"C003\",\"EX901\"]")
                .currentSkillDeck(List.of("C001", "C002", "C003"))
                .exCard("{\"id\":\"EX901\"}")
                .build());
        return profile.getId();
    }

    private MockHttpSession signUpAndLogin(String username, String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, email, password)))
                .andExpect(status().isOk());

        HttpSession session = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "%s"
                                }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);

        assertNotNull(session);
        return (MockHttpSession) session;
    }

    private long createPreset(MockHttpSession session, long characterId, String name) throws Exception {
        MvcResult createResult = mockMvc.perform(post("/api/me/presets")
                        .session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "%s",
                                  "characterId": %d,
                                  "deckCardIds": ["C001", "C002", "C003"],
                                  "exCardId": "EX901",
                                  "passiveIds": ["P001"]
                                }
                                """.formatted(name, characterId)))
                .andExpect(status().isOk())
                .andReturn();

        return readPresetId(createResult);
    }

    private long readPresetId(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+)").matcher(body);
        if (!m.find()) {
            throw new IllegalStateException("missing id in response: " + body);
        }
        return Long.parseLong(m.group(1));
    }
}
