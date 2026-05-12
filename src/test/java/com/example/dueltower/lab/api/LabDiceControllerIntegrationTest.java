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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LabDiceControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/lab/dice는 인증 없이 주사위 계산 결과를 반환한다")
    void calculateDiceShouldBeAccessibleAsLabApi() throws Exception {
        mockMvc.perform(post("/api/lab/dice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notation": "3d6+2",
                                  "rollCount": 0,
                                  "seed": 12345
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notation").value("3d6+2"))
                .andExpect(jsonPath("$.spec.count").value(3))
                .andExpect(jsonPath("$.spec.sides").value(6))
                .andExpect(jsonPath("$.spec.modifier").value(2))
                .andExpect(jsonPath("$.min").value(5))
                .andExpect(jsonPath("$.max").value(20))
                .andExpect(jsonPath("$.expected").value("12.5"))
                .andExpect(jsonPath("$.expectedNumerator").value(25))
                .andExpect(jsonPath("$.expectedDenominator").value(2))
                .andExpect(jsonPath("$.rollCount").value(0))
                .andExpect(jsonPath("$.seed").value(12345))
                .andExpect(jsonPath("$.rolls").isEmpty())
                .andExpect(jsonPath("$.histogram").isEmpty());
    }

    @Test
    @DisplayName("잘못된 notation은 공통 에러 형식의 400 응답을 반환한다")
    void calculateDiceShouldReturnBadRequestForInvalidNotation() throws Exception {
        mockMvc.perform(post("/api/lab/dice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notation": "2x6"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.userMessage").value(containsString("invalid dice notation")));
    }

    @Test
    @DisplayName("rollCount 범위 초과는 공통 에러 형식의 400 응답을 반환한다")
    void calculateDiceShouldReturnBadRequestForTooLargeRollCount() throws Exception {
        mockMvc.perform(post("/api/lab/dice")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "notation": "d20",
                                  "rollCount": 1001
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.userMessage").value(containsString("rollCount")));
    }
}
