package com.example.dueltower.session.api;

import com.example.dueltower.member.MemberRepository;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SessionGmAccessRestoreIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MemberRepository memberRepository;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
    }

    @Test
    @DisplayName("로그인된 원래 GM은 gm access restore를 성공하고 gmToken과 state를 받는다")
    void originalGmCanRestoreAccessAndReceiveTokenAndState() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");

        MvcResult createResult = mockMvc.perform(post("/api/sessions")
                        .session(gmSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gmId\":\"gm\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String code = extractJsonStringValue(createResult.getResponse().getContentAsString(), "code");
        String gmToken = extractJsonStringValue(createResult.getResponse().getContentAsString(), "gmToken");

        mockMvc.perform(post("/api/sessions/{code}/gm-access/restore", code)
                        .session(gmSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.gmToken").value(gmToken))
                .andExpect(jsonPath("$.state.sessionCode").value(code))
                .andExpect(jsonPath("$.state.version").isNumber());
    }

    @Test
    @DisplayName("다른 로그인 사용자는 gm access restore를 할 수 없다")
    void otherAuthenticatedUserCannotRestoreAccess() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");
        MockHttpSession otherSession = signUpAndLogin("other", "other@example.com", "password123");

        MvcResult createResult = mockMvc.perform(post("/api/sessions")
                        .session(gmSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gmId\":\"gm\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String code = extractJsonStringValue(createResult.getResponse().getContentAsString(), "code");

        mockMvc.perform(post("/api/sessions/{code}/gm-access/restore", code)
                        .session(otherSession))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("비로그인 사용자는 gm access restore를 할 수 없다")
    void anonymousUserCannotRestoreAccess() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");

        MvcResult createResult = mockMvc.perform(post("/api/sessions")
                        .session(gmSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gmId\":\"gm\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String code = extractJsonStringValue(createResult.getResponse().getContentAsString(), "code");

        mockMvc.perform(post("/api/sessions/{code}/gm-access/restore", code))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("restore 응답의 gmToken은 현재 세션의 활성 GM token과 일치한다")
    void restoreReturnsCurrentActiveGmToken() throws Exception {
        MockHttpSession gmSession = signUpAndLogin("gm", "gm@example.com", "password123");

        MvcResult createResult = mockMvc.perform(post("/api/sessions")
                        .session(gmSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"gmId\":\"gm\"}"))
                .andExpect(status().isOk())
                .andReturn();

        String code = extractJsonStringValue(createResult.getResponse().getContentAsString(), "code");
        String createdToken = extractJsonStringValue(createResult.getResponse().getContentAsString(), "gmToken");

        mockMvc.perform(post("/api/sessions/{code}/gm-access/restore", code)
                        .session(gmSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gmToken").value(createdToken));
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

    private String extractJsonStringValue(String json, String key) {
        Pattern p = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
        Matcher m = p.matcher(json);
        if (!m.find()) {
            throw new IllegalStateException("missing json string key: " + key + " in " + json);
        }
        return m.group(1);
    }
}
