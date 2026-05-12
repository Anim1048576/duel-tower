package com.example.dueltower.lab.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LabEffectProbeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/lab/effects/cards는 인증 없이 Probe 가능한 카드 목록을 반환한다")
    void cardsShouldBeAccessibleAsLabApi() throws Exception {
        mockMvc.perform(get("/api/lab/effects/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cardId").exists())
                .andExpect(jsonPath("$[0].type").value("SKILL"));
    }

    @Test
    @DisplayName("POST /api/lab/effects/probe는 인증 없이 효과 Probe 결과를 반환한다")
    void probeShouldBeAccessibleAsLabApi() throws Exception {
        mockMvc.perform(post("/api/lab/effects/probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cardId": "C001",
                                  "actor": {
                                    "attackPower": 7,
                                    "healPower": 5,
                                    "hp": 20,
                                    "maxHp": 20
                                  },
                                  "target": {
                                    "kind": "ENEMY",
                                    "id": "dummy_enemy",
                                    "hp": 30,
                                    "maxHp": 30,
                                    "statuses": {
                                      "SHIELD": 3
                                    }
                                  },
                                  "selection": {
                                    "targets": [
                                      { "kind": "ENEMY", "id": "dummy_enemy" }
                                    ],
                                    "discardIds": [],
                                    "selectedIds": [],
                                    "choiceId": null
                                  },
                                  "seed": 12345,
                                  "validateOnly": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value("C001"))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.resolved").value(true))
                .andExpect(jsonPath("$.after.targets[0].hp").value(26))
                .andExpect(jsonPath("$.notes").value(hasItem(containsString("AP cost"))));
    }

    @Test
    @DisplayName("POST /api/lab/effects/probe는 targets 배열과 alias 기반 extraCards를 받는다")
    void probeShouldAcceptMultipleTargetsAndExtraCardAliases() throws Exception {
        mockMvc.perform(post("/api/lab/effects/probe")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "cardId": "Tig005_Card",
                                  "actor": {
                                    "attackPower": 7,
                                    "healPower": 5,
                                    "hp": 20,
                                    "maxHp": 20,
                                    "statuses": {
                                      "Tig201_Status": 3
                                    }
                                  },
                                  "targets": [
                                    { "kind": "ENEMY", "id": "enemy_a", "hp": 30, "maxHp": 30 },
                                    { "kind": "ENEMY", "id": "enemy_b", "hp": 25, "maxHp": 25 }
                                  ],
                                  "selection": {
                                    "discardAliases": ["hand_1"],
                                    "selectedAliases": []
                                  },
                                  "extraCards": [
                                    { "alias": "hand_1", "cardId": "C001", "zone": "HAND" }
                                  ],
                                  "seed": 12345,
                                  "validateOnly": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value("Tig005_Card"))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.resolved").value(true))
                .andExpect(jsonPath("$.after.targets[0].hp").value(20))
                .andExpect(jsonPath("$.after.targets[1].hp").value(15))
                .andExpect(jsonPath("$.notes").value(hasItem(containsString("Target states created: 2"))));
    }
}
