package com.example.dueltower.auth.api;

import com.example.dueltower.member.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("회원가입, 로그인, 내 정보 조회 흐름이 동작한다")
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
    @DisplayName("보호된 인증 엔드포인트는 공개 접근되면 안 된다")
    void protectedAuthEndpointsShouldNotBePublic() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized());
    }
    @Test
    @DisplayName("중복 username이면 실패한다")
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
    @DisplayName("회원가입은 요청 본문이 null이면 BAD_REQUEST를 반환한다")
    void signupWithNullBodyShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("회원가입은 username이 blank면 BAD_REQUEST를 반환한다")
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
    @DisplayName("회원가입은 password가 blank면 BAD_REQUEST를 반환한다")
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
    @DisplayName("로그인은 요청 본문이 null이면 BAD_REQUEST를 반환한다")
    void loginWithNullBodyShouldReturnBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("로그인은 username이 blank면 BAD_REQUEST를 반환한다")
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
    @DisplayName("로그인은 password가 blank면 BAD_REQUEST를 반환한다")
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
    @DisplayName("로그인은 비밀번호가 틀리면 UNAUTHORIZED를 반환한다")
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
    @DisplayName("로그아웃은 세션을 무효화한다")
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
    @DisplayName("회원가입은 username을 trim해서 저장한다")
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
    @DisplayName("로그인은 username을 trim해서 처리한다")
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
