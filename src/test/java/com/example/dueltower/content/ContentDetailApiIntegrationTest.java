package com.example.dueltower.content;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.item.service.ItemService;
import com.example.dueltower.content.keyword.service.KeywordService;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.content.status.service.StatusService;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.ItemDefinition;
import com.example.dueltower.engine.model.KeywordDefinition;
import com.example.dueltower.engine.model.PassiveDefinition;
import com.example.dueltower.engine.model.StatusDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentDetailApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CardService cardService;

    @Autowired
    private PassiveService passiveService;

    @Autowired
    private StatusService statusService;

    @Autowired
    private KeywordService keywordService;

    @Autowired
    private ItemService itemService;

    @Test
    @DisplayName("카드 상세는 로그인 없이 접근 가능하고 핵심 필드를 포함한다")
    void cardDetailShouldBeAccessibleWithoutLoginAndContainCoreFields() throws Exception {
        CardDefinition knownCard = cardService.list().stream().findFirst().orElseThrow();
        String cardId = knownCard.id().value();

        mockMvc.perform(get("/api/content/cards/{id}", cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id.value").value(cardId))
                .andExpect(jsonPath("$.name", not(emptyOrNullString())))
                .andExpect(jsonPath("$.type").isString())
                .andExpect(jsonPath("$.cost").isNumber())
                .andExpect(jsonPath("$.keywords").isMap())
                .andExpect(jsonPath("$.resolveTo").isString())
                .andExpect(jsonPath("$.token").isBoolean())
                .andExpect(jsonPath("$.description", not(emptyOrNullString())))
                .andExpect(jsonPath("$.playSpec").exists())
                .andExpect(jsonPath("$.playSpec.target").exists())
                .andExpect(jsonPath("$.playSpec.extraRequirements").isArray());
    }


    @Test
    @DisplayName("카드 상세는 trim된 ID 조회를 허용한다")
    void cardDetailShouldAllowTrimmedIdLookup() throws Exception {
        CardDefinition knownCard = cardService.list().stream().findFirst().orElseThrow();
        String padded = " " + knownCard.id().value() + " ";

        mockMvc.perform(get("/api/content/cards/{id}", padded))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id.value").value(knownCard.id().value()));
    }

    @Test
    @DisplayName("카드 상세는 알 수 없는 ID에 대해 NOT_FOUND를 반환한다")
    void cardDetailShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/cards/{id}", "__UNKNOWN_CARD__"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("카드 상세는 TIG discard 카드의 play spec 메타데이터를 노출한다")
    void cardDetailShouldExposePlaySpecMetadataForTigDiscardCards() throws Exception {
        mockMvc.perform(get("/api/content/cards/{id}", "Tig004_Card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playSpec.target.target").value("ENEMY_ONE"))
                .andExpect(jsonPath("$.playSpec.target.requiredSelection").value(true))
                .andExpect(jsonPath("$.playSpec.extraRequirements.length()").value(1));

        mockMvc.perform(get("/api/content/cards/{id}", "Tig005_Card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playSpec.target.target").value("NONE"))
                .andExpect(jsonPath("$.playSpec.target.requiredSelection").value(false))
                .andExpect(jsonPath("$.playSpec.extraRequirements.length()").value(1));

        mockMvc.perform(get("/api/content/cards/{id}", "Tig006_Card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playSpec.target.target").value("NONE"))
                .andExpect(jsonPath("$.playSpec.target.requiredSelection").value(false))
                .andExpect(jsonPath("$.playSpec.extraRequirements.length()").value(2));

        mockMvc.perform(get("/api/content/cards/{id}", "Tig008_Card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playSpec.target.target").value("ENEMY_ONE"))
                .andExpect(jsonPath("$.playSpec.target.requiredSelection").value(true))
                .andExpect(jsonPath("$.playSpec.extraRequirements.length()").value(1));
    }

    @Test
    @DisplayName("카드 상세는 TIG001의 installed selection play spec을 노출한다")
    void cardDetailShouldExposeInstalledSelectionPlaySpecForTig001() throws Exception {
        mockMvc.perform(get("/api/content/cards/{id}", "Tig001_Card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playSpec.target.target").value("ENEMY_ONE"))
                .andExpect(jsonPath("$.playSpec.target.requiredSelection").value(true))
                .andExpect(jsonPath("$.playSpec.extraRequirements.length()").value(1))
                .andExpect(jsonPath("$.playSpec.extraRequirements[0].type").value("select_field_cards"))
                .andExpect(jsonPath("$.playSpec.extraRequirements[0].minSelections").value(0))
                .andExpect(jsonPath("$.playSpec.extraRequirements[0].maxSelections").value(1))
                .andExpect(jsonPath("$.playSpec.extraRequirements[0].scope").value("ALL_PLAYER_FIELDS"))
                .andExpect(jsonPath("$.playSpec.extraRequirements[0].filter").value("INSTALLED_ONLY"))
                .andExpect(jsonPath("$.playSpec.extraRequirements[0].excludeSourceCard").value(true));
    }

    @Test
    @DisplayName("카드 상세는 TIG006의 discard와 selection 요구사항을 모두 노출한다")
    void cardDetailShouldExposeBothDiscardAndSelectionRequirementsForTig006() throws Exception {
        mockMvc.perform(get("/api/content/cards/{id}", "Tig006_Card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playSpec.target.target").value("NONE"))
                .andExpect(jsonPath("$.playSpec.target.requiredSelection").value(false))
                .andExpect(jsonPath("$.playSpec.extraRequirements.length()").value(2))
                .andExpect(jsonPath("$.playSpec.extraRequirements[0].type").value("discard_from_hand"))
                .andExpect(jsonPath("$.playSpec.extraRequirements[1].type").value("select_field_cards"))
                .andExpect(jsonPath("$.playSpec.extraRequirements[1].minSelections").value(0))
                .andExpect(jsonPath("$.playSpec.extraRequirements[1].maxSelections").value(3))
                .andExpect(jsonPath("$.playSpec.extraRequirements[1].scope").value("ALL_PLAYER_FIELDS"))
                .andExpect(jsonPath("$.playSpec.extraRequirements[1].filter").value("INSTALLED_ONLY"))
                .andExpect(jsonPath("$.playSpec.extraRequirements[1].excludeSourceCard").value(true));
    }

    @Test
    @DisplayName("카드 상세는 기본 카드의 none play spec을 노출한다")
    void cardDetailShouldExposeNonePlaySpecForDefaultCard() throws Exception {
        mockMvc.perform(get("/api/content/cards/{id}", "C001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playSpec.target.target").value("NONE"))
                .andExpect(jsonPath("$.playSpec.target.requiredSelection").value(false))
                .andExpect(jsonPath("$.playSpec.extraRequirements.length()").value(0));
    }

    @Test
    @DisplayName("패시브 상세는 알려진 ID에 대해 정상 응답을 반환한다")
    void passiveDetailShouldReturnOkForKnownId() throws Exception {
        PassiveDefinition knownPassive = passiveService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/passives/{id}", knownPassive.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(knownPassive.id()))
                .andExpect(jsonPath("$.name", not(emptyOrNullString())))
                .andExpect(jsonPath("$.description", not(emptyOrNullString())));
    }

    @Test
    @DisplayName("패시브 상세는 trim된 ID 조회를 허용한다")
    void passiveDetailShouldAllowTrimmedIdLookup() throws Exception {
        PassiveDefinition knownPassive = passiveService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/passives/{id}", " " + knownPassive.id() + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(knownPassive.id()));
    }

    @Test
    @DisplayName("패시브 상세는 알 수 없는 ID에 대해 NOT_FOUND를 반환한다")
    void passiveDetailShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/passives/{id}", "__UNKNOWN_PASSIVE__"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("상태 상세는 알려진 ID에 대해 정상 응답을 반환한다")
    void statusDetailShouldReturnOkForKnownId() throws Exception {
        StatusDefinition knownStatus = statusService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/statuses/{id}", knownStatus.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(knownStatus.id()))
                .andExpect(jsonPath("$.name", not(emptyOrNullString())))
                .andExpect(jsonPath("$.description", not(emptyOrNullString())));
    }

    @Test
    @DisplayName("상태 상세는 trim된 ID 조회를 허용한다")
    void statusDetailShouldAllowTrimmedIdLookup() throws Exception {
        StatusDefinition knownStatus = statusService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/statuses/{id}", " " + knownStatus.id() + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(knownStatus.id()));
    }

    @Test
    @DisplayName("상태 상세는 알 수 없는 ID에 대해 NOT_FOUND를 반환한다")
    void statusDetailShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/statuses/{id}", "__UNKNOWN_STATUS__"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("키워드 상세는 알려진 ID에 대해 정상 응답을 반환한다")
    void keywordDetailShouldReturnOkForKnownId() throws Exception {
        KeywordDefinition knownKeyword = keywordService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/keywords/{id}", knownKeyword.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(knownKeyword.id()))
                .andExpect(jsonPath("$.name", not(emptyOrNullString())))
                .andExpect(jsonPath("$.description", not(emptyOrNullString())));
    }

    @Test
    @DisplayName("키워드 상세는 trim된 ID 조회를 허용한다")
    void keywordDetailShouldAllowTrimmedIdLookup() throws Exception {
        KeywordDefinition knownKeyword = keywordService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/keywords/{id}", " " + knownKeyword.id() + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(knownKeyword.id()));
    }

    @Test
    @DisplayName("키워드 상세는 알 수 없는 ID에 대해 NOT_FOUND를 반환한다")
    void keywordDetailShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/keywords/{id}", "__UNKNOWN_KEYWORD__"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("아이템 상세는 알려진 ID에 대해 정상 응답을 반환한다")
    void itemDetailShouldReturnOkForKnownId() throws Exception {
        ItemDefinition knownItem = itemService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/items/{id}", knownItem.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(knownItem.id()))
                .andExpect(jsonPath("$.name", not(emptyOrNullString())))
                .andExpect(jsonPath("$.summary", not(emptyOrNullString())))
                .andExpect(jsonPath("$.description", not(emptyOrNullString())))
                .andExpect(jsonPath("$.battleUsable").isBoolean())
                .andExpect(jsonPath("$.tags").isArray());
    }

    @Test
    @DisplayName("아이템 상세는 trim된 ID 조회를 허용한다")
    void itemDetailShouldAllowTrimmedIdLookup() throws Exception {
        ItemDefinition knownItem = itemService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/items/{id}", " " + knownItem.id() + " "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(knownItem.id()));
    }

    @Test
    @DisplayName("아이템 상세는 알 수 없는 ID에 대해 NOT_FOUND를 반환한다")
    void itemDetailShouldReturnNotFoundForUnknownId() throws Exception {
        mockMvc.perform(get("/api/content/items/{id}", "__UNKNOWN_ITEM__"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("아이템 목록은 등록된 아이템을 반환한다")
    void itemListShouldReturnRegisteredItems() throws Exception {
        mockMvc.perform(get("/api/content/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(5)))
                .andExpect(jsonPath("$[*].id", hasItems("I-1", "I-2", "I-3", "I-4", "I-5")));
    }

    @Test
    @DisplayName("콘텐츠 상세 엔드포인트와 카드 목록은 공개 접근을 유지한다")
    void contentDetailEndpointsAndCardListShouldRemainPublic() throws Exception {
        CardDefinition knownCard = cardService.list().stream().findFirst().orElseThrow();
        PassiveDefinition knownPassive = passiveService.list().stream().findFirst().orElseThrow();
        StatusDefinition knownStatus = statusService.list().stream().findFirst().orElseThrow();
        KeywordDefinition knownKeyword = keywordService.list().stream().findFirst().orElseThrow();
        ItemDefinition knownItem = itemService.list().stream().findFirst().orElseThrow();

        mockMvc.perform(get("/api/content/cards"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/content/cards/{id}", knownCard.id().value()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/content/passives/{id}", knownPassive.id()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/content/statuses/{id}", knownStatus.id()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/content/keywords/{id}", knownKeyword.id()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/content/items"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/content/items/{id}", knownItem.id()))
                .andExpect(status().isOk());
    }
}
