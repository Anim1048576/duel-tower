package com.example.dueltower.screen.dto;

import com.example.dueltower.common.api.ApiErrorResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScreenDtoContractTest {

    @Test
    void disabledReasonCanMirrorApiErrorResponse() {
        ApiErrorResponse error = ApiErrorResponse.of(
                "RULE_NOT_TURN_OWNER",
                "RULE",
                "지금은 행동할 수 없습니다.",
                "runtimePlayerId != currentTurnPlayerId",
                Map.of("runtimePlayerId", "p1", "currentTurnPlayerId", "p2"),
                409,
                "/api/sessions/ABCD1234/command"
        );

        DisabledReasonDto reason = DisabledReasonDto.fromApiErrorResponse(error);

        assertThat(reason.code()).isEqualTo(error.code());
        assertThat(reason.category()).isEqualTo(error.category());
        assertThat(reason.userMessage()).isEqualTo(error.userMessage());
        assertThat(reason.debugMessage()).isEqualTo(error.debugMessage());
        assertThat(reason.details()).isEqualTo(error.details());
        assertThat(reason.status()).isEqualTo(error.status());
        assertThat(reason.path()).isEqualTo(error.path());
    }

    @Test
    void actionFactoryUsesWireAuthValueAndNormalizesMethod() {
        Map<String, Object> payloadTemplate = new LinkedHashMap<>();
        payloadTemplate.put("startPlayerId", "p1");

        ScreenActionDto action = ScreenActionDto.of(
                "gmLobby.startCombat",
                "Start combat",
                "post",
                "/api/screens/sessions/ABCD1234/gm-lobby/start-combat",
                ScreenActionAuth.GM_TOKEN,
                false,
                new DisabledReasonDto(
                        "START_COMBAT_BLOCKED",
                        "RULE",
                        "전투를 시작할 수 없습니다.",
                        "recommendedStartPlayerId is null",
                        null,
                        null,
                        null
                ),
                payloadTemplate
        );

        assertThat(action.method()).isEqualTo("POST");
        assertThat(action.auth()).isEqualTo("gmToken");
        assertThat(action.payloadTemplate()).containsEntry("startPlayerId", "p1");
        assertThat(action.metadata()).isNull();
        assertThatThrownBy(() -> action.payloadTemplate().put("startPlayerId", "p2"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void baseDefaultsOptionalCollectionsToEmptyLists() {
        ScreenResponseBase base = new ScreenResponseBase(
                "PlayerLobby",
                OffsetDateTime.parse("2026-04-15T10:20:30+09:00"),
                null,
                null
        );

        assertThat(base.getUiNotices()).isEmpty();
        assertThat(base.getPossibleActions()).isEmpty();
    }

    @Test
    void combatScreenResponseCanCarryCuratedReadModelSlices() {
        CombatScreenResponse response = new CombatScreenResponse(
                "Combat",
                OffsetDateTime.parse("2026-04-18T19:10:30+09:00"),
                List.of("combat screen sample"),
                List.of(),
                "ABCD1234",
                27L,
                true,
                new CombatScreenResponse.Status(
                        2,
                        "PLAYER",
                        new CombatScreenResponse.ActorSummary("P:p1", "player", "p1", "p1", "Current player turn", "success"),
                        "p1 -> e1",
                        "1 players | 1 enemies",
                        "Current node: test encounter",
                        null
                ),
                new CombatScreenResponse.Access(
                        "player",
                        "p1",
                        27L,
                        new CombatScreenResponse.GuardSummary(true, false, true, false, true, false, true, true)
                ),
                new CombatScreenResponse.Actors(
                        List.of(),
                        List.of(),
                        List.of()
                ),
                new CombatScreenResponse.Zones(
                        "p1",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        null
                ),
                new CombatScreenResponse.Sidebar(
                        List.of(),
                        List.of(),
                        List.of()
                )
        );

        assertThat(response.getSessionCode()).isEqualTo("ABCD1234");
        assertThat(response.getVersion()).isEqualTo(27L);
        assertThat(response.isChanged()).isTrue();
        assertThat(response.getStatus().currentActor().label()).isEqualTo("p1");
        assertThat(response.getAccess().guards().canIssuePlayerCommand()).isTrue();
        assertThat(response.getZones().visiblePlayerId()).isEqualTo("p1");
        assertThat(response.getSidebar().events()).isEmpty();
    }
}
