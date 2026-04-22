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

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
