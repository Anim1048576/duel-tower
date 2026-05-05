package com.example.dueltower.session.api;

import com.example.dueltower.member.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "dueltower.debug.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DebugSoloCombatIntegrationTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    void debugSoloCombatCreatesSessionWithGmControlledNpcAndStartsCombat() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/debug/sessions/solo-combat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionCode").isString())
                .andExpect(jsonPath("$.gmPlayerId").value("debug-gm"))
                .andExpect(jsonPath("$.npcPlayerId").value("gm-npc-1"))
                .andExpect(jsonPath("$.gmToken").isString())
                .andExpect(jsonPath("$.playerToken").isString())
                .andReturn();

        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        String code = body.path("sessionCode").asText();

        mockMvc.perform(get("/api/sessions/{code}", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodeState").value("COMBAT"))
                .andExpect(jsonPath("$.players.debug-gm.controlType").value("HUMAN"))
                .andExpect(jsonPath("$.players.gm-npc-1.controlType").value("GM_CONTROLLED_NPC"))
                .andExpect(jsonPath("$.players.gm-npc-1.controllerPlayerId").value("debug-gm"))
                .andExpect(jsonPath("$.combat.turnOrder").isArray());
    }
}
