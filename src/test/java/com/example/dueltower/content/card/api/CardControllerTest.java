package com.example.dueltower.content.card.api;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Zone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardController.class)
@AutoConfigureMockMvc(addFilters = false)
class CardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CardService cardService;

    @Test
    void listReturnsAllCardsWhenTypeIsOmitted() throws Exception {
        CardDefinition skillCard = new CardDefinition(
                new Ids.CardDefId("C001"),
                "Basic Attack",
                CardType.SKILL,
                1,
                Map.of(),
                Zone.DISCARD,
                false,
                "Deal 1 damage"
        );
        given(cardService.list()).willReturn(List.of(skillCard));

        mockMvc.perform(get("/api/content/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("C001"))
                .andExpect(jsonPath("$[0].type").value("SKILL"));

        then(cardService).should().list();
    }

    @Test
    void listReturnsFilteredCardsWhenTypeIsProvided() throws Exception {
        CardDefinition exCard = new CardDefinition(
                new Ids.CardDefId("EX901"),
                "Bandage Wrap",
                CardType.EX,
                0,
                Map.of(),
                Zone.DISCARD,
                false,
                "Recover 2 HP"
        );
        given(cardService.list(CardType.EX)).willReturn(List.of(exCard));

        mockMvc.perform(get("/api/content/cards").param("type", "EX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("EX901"))
                .andExpect(jsonPath("$[0].type").value("EX"));

        then(cardService).should().list(CardType.EX);
    }
}
