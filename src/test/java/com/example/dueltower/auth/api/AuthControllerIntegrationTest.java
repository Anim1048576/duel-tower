package com.example.dueltower.auth.api;

import com.example.dueltower.member.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import jakarta.servlet.http.HttpSession;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    void signupAndLoginAndMeFlow() throws Exception {
        String signupBody = """
                {
                  "username": "tester",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("tester"));

        String loginBody = """
                {
                  "username": "tester",
                  "password": "password123"
                }
                """;

        HttpSession session = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("tester"))
                .andReturn()
                .getRequest()
                .getSession(false);

        assertNotNull(session);

        mockMvc.perform(get("/api/auth/me").session((MockHttpSession) session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("tester"));
    }


    @Test
    void protectedAuthEndpointsShouldNotBePublic() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }
    @Test
    void duplicateUsernameShouldFail() throws Exception {
        String signupBody = """
                {
                  "username": "tester",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isOk());

        String duplicateBody = """
                {
                  "username": "tester",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(duplicateBody))
                .andExpect(status().isConflict());
    }
}
