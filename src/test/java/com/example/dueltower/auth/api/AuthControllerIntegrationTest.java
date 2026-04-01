package com.example.dueltower.auth.api;

import com.example.dueltower.member.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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

    @Test
    void signupWithNullBodyShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signupWithBlankUsernameShouldReturnBadRequest() throws Exception {
        String signupBody = """
                {
                  "username": "   ",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void signupWithBlankPasswordShouldReturnBadRequest() throws Exception {
        String signupBody = """
                {
                  "username": "tester",
                  "password": "   "
                }
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithNullBodyShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithBlankUsernameShouldReturnBadRequest() throws Exception {
        String loginBody = """
                {
                  "username": "   ",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithBlankPasswordShouldReturnBadRequest() throws Exception {
        String loginBody = """
                {
                  "username": "tester",
                  "password": "   "
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithWrongPasswordShouldReturnUnauthorized() throws Exception {
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

        String loginBody = """
                {
                  "username": "tester",
                  "password": "wrong-password"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutShouldInvalidateSession() throws Exception {
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

        String loginBody = """
                {
                  "username": "tester",
                  "password": "password123"
                }
                """;

        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn()
                .getRequest()
                .getSession(false);

        assertNotNull(session);

        mockMvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void signupShouldTrimUsername() throws Exception {
        String signupBody = """
                {
                  "username": "  tester  ",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("tester"));
    }

    @Test
    void loginShouldTrimUsername() throws Exception {
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

        String loginBody = """
                {
                  "username": "  tester  ",
                  "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("tester"));
    }
}
