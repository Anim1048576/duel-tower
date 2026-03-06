package com.example.dueltower.character.api;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.dto.CharacterProfileResponse;
import com.example.dueltower.character.dto.CombatStatsDto;
import com.example.dueltower.character.service.CharacterProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CharacterProfileController.class)
@AutoConfigureMockMvc(addFilters = false)
class CharacterProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CharacterProfileService characterProfileService;

    @Test
    void listReturnsCharacterProfiles() throws Exception {
        CharacterProfileResponse response = new CharacterProfileResponse(
                1L,
                "티그",
                CharacterGender.FEMALE,
                18,
                "소중한 사람을 지키고 싶다",
                "낙천적",
                "오늘도 전진!",
                "짧은 이야기",
                12,
                8,
                10,
                7,
                "강심장",
                "속전속결",
                "{\"cards\":[\"C001\"]}",
                "{\"deck\":[\"C001\"]}",
                "{\"id\":\"EX001\"}",
                new CombatStatsDto(87, 4, 25, 24),
                Timestamp.valueOf("2026-01-01 00:00:00"),
                Timestamp.valueOf("2026-01-01 00:00:00")
        );
        given(characterProfileService.list()).willReturn(List.of(response));

        mockMvc.perform(get("/api/content/characters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L))
                .andExpect(jsonPath("$[0].name").value("티그"))
                .andExpect(jsonPath("$[0].combatStats.maxHp").value(87));
    }

    @Test
    void createReturns400WhenRequestBodyIsMissing() throws Exception {
        given(characterProfileService.create(any()))
                .willThrow(new ResponseStatusException(BAD_REQUEST, "request body is required"));

        mockMvc.perform(post("/api/content/characters")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(status().reason("request body is required"));
    }
}
