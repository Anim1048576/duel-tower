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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    void startSoloCombat_createsSingleDebugPlayerWithoutGmNpc() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/debug/sessions/solo-combat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
        String code = body.path("sessionCode").asText();
        assertThat(code).isNotBlank();
        assertThat(body.path("gmPlayerId").asText()).isEqualTo("debug-gm");
        assertThat(body.path("npcPlayerId").isNull()).isTrue();
        assertThat(body.path("gmToken").asText()).isNotBlank();
        assertThat(body.path("playerToken").asText()).isNotBlank();
        assertThat(body.path("redirectUrl").asText()).isEqualTo("/sessions/" + code + "/combat");

        MvcResult sessionResult = mockMvc.perform(get("/api/sessions/{code}", code))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode sessionBody = JSON.readTree(sessionResult.getResponse().getContentAsString());
        assertThat(sessionBody.path("nodeState").asText()).isEqualTo("COMBAT");
        assertThat(sessionBody.path("players")).hasSize(1);
        assertThat(sessionBody.path("players").has("debug-gm")).isTrue();
        assertThat(sessionBody.path("players").path("debug-gm").path("controlType").asText()).isEqualTo("HUMAN");
        assertThat(sessionBody.path("players").path("debug-gm").path("ready").asBoolean()).isTrue();
        assertThat(controlTypes(sessionBody.path("players"))).doesNotContain("GM_CONTROLLED_NPC");
        assertThat(sessionBody.path("combat").path("turnOrder")).isNotEmpty();
    }

    @Test
    void startSoloCombat_usesDebugPlayerAsCombatScreenActorAndSourceOwner() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/debug/sessions/solo-combat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode responseBody = JSON.readTree(result.getResponse().getContentAsString());
        String code = responseBody.path("sessionCode").asText();
        String playerToken = responseBody.path("playerToken").asText();

        MvcResult screenResult = mockMvc.perform(get("/api/screens/sessions/{code}/combat", code)
                        .header("X-Player-Token", playerToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode screen = JSON.readTree(screenResult.getResponse().getContentAsString());
        JsonNode playCardAction = findAction(screen, "combat.playCard");
        Set<String> visibleHandIds = instanceIds(screen.path("zones").path("hand"));
        Set<String> sourceOptionIds = instanceIds(playCardAction.path("metadata").path("sourceOptions"));

        assertThat(screen.path("actors").path("players")).hasSize(1);
        assertThat(screen.path("actors").path("players").get(0).path("playerId").asText()).isEqualTo("debug-gm");
        assertThat(screen.path("access").path("runtimePlayerId").asText()).isEqualTo("debug-gm");
        assertThat(screen.path("access").path("guards").path("hasCombatState").asBoolean()).isTrue();
        assertThat(screen.path("zones").path("visiblePlayerId").asText()).isEqualTo("debug-gm");
        assertThat(screen.path("status").path("currentActor").path("id").asText()).isEqualTo("debug-gm");
        assertThat(playCardAction.path("payloadTemplate").path("playerId").asText()).isEqualTo("debug-gm");
        assertThat(playCardAction.path("payloadTemplate").path("type").asText()).isEqualTo("PLAY_CARD");
        assertThat(visibleHandIds).isEqualTo(sourceOptionIds);
        if ("debug-gm".equals(screen.path("status").path("currentActor").path("id").asText())) {
            assertThat(screen.path("access").path("guards").path("canIssuePlayerCommand").asBoolean()).isTrue();
        }
        assertThat(findAction(screen, "combat.draw").path("id").asText()).isEqualTo("combat.draw");
        assertThat(findAction(screen, "combat.endTurn").path("id").asText()).isEqualTo("combat.endTurn");
        assertThat(findAction(screen, "combat.useEx").path("id").asText()).isEqualTo("combat.useEx");
        assertThat(findAction(screen, "combat.resolvePending").path("id").asText()).isEqualTo("combat.resolvePending");
    }

    private static JsonNode findAction(JsonNode body, String actionId) {
        return StreamSupport.stream(body.path("possibleActions").spliterator(), false)
                .filter(action -> actionId.equals(action.path("id").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing action " + actionId));
    }

    private static Set<String> instanceIds(JsonNode nodes) {
        Set<String> ids = new LinkedHashSet<>();
        nodes.forEach(node -> ids.add(node.path("instanceId").asText()));
        return ids;
    }

    private static Set<String> controlTypes(JsonNode players) {
        Set<String> types = new LinkedHashSet<>();
        players.forEach(player -> types.add(player.path("controlType").asText()));
        return types;
    }
}
