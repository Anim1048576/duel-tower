package com.example.dueltower.screen.dto;

import com.example.dueltower.common.api.ApiErrorResponse;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
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
}
