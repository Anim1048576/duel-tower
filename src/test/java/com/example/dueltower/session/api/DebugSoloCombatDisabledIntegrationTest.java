package com.example.dueltower.session.api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "dueltower.debug.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DebugSoloCombatDisabledIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void debugSoloCombatEndpointIsNotExposedWhenDisabled() throws Exception {
        mockMvc.perform(post("/api/debug/sessions/solo-combat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());
    }
}
