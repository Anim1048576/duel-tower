package com.example.dueltower.engine.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PendingDecisionSearchPickTest {

    @Test
    @DisplayName("SearchPick 생성 시 후보/목적지/개수 불변식을 만족하면 정상 생성된다")
    void createSearchPickWithValidArguments() {
        List<Ids.CardInstId> candidates = List.of(Ids.newCardInstId(), Ids.newCardInstId());

        assertDoesNotThrow(() -> new PendingDecision.SearchPick(
                "search from deck",
                candidates,
                1,
                Zone.HAND,
                true,
                null
        ));
    }

    @Test
    @DisplayName("SearchPick 생성 시 후보 목록에 중복 ID가 있으면 예외가 발생한다")
    void rejectDuplicatedCandidates() {
        Ids.CardInstId duplicate = Ids.newCardInstId();

        assertThrows(IllegalArgumentException.class, () -> new PendingDecision.SearchPick(
                "search from deck",
                List.of(duplicate, duplicate),
                1,
                Zone.HAND,
                false,
                null
        ));
    }

    @Test
    @DisplayName("SearchPick 생성 시 pickCount가 유효 범위를 벗어나면 예외가 발생한다")
    void rejectInvalidPickCount() {
        List<Ids.CardInstId> candidates = List.of(Ids.newCardInstId(), Ids.newCardInstId());

        assertThrows(IllegalArgumentException.class, () -> new PendingDecision.SearchPick(
                "search from deck",
                candidates,
                0,
                Zone.HAND,
                false,
                null
        ));

        assertThrows(IllegalArgumentException.class, () -> new PendingDecision.SearchPick(
                "search from deck",
                candidates,
                3,
                Zone.HAND,
                false,
                null
        ));
    }

    @Test
    @DisplayName("SearchPick 생성 시 reason/candidateIds/destination이 null이면 예외가 발생한다")
    void rejectNullMandatoryArguments() {
        List<Ids.CardInstId> candidates = List.of(Ids.newCardInstId());

        assertThrows(NullPointerException.class, () -> new PendingDecision.SearchPick(
                null,
                candidates,
                1,
                Zone.HAND,
                false,
                null
        ));

        assertThrows(NullPointerException.class, () -> new PendingDecision.SearchPick(
                "search from deck",
                null,
                1,
                Zone.HAND,
                false,
                null
        ));

        assertThrows(NullPointerException.class, () -> new PendingDecision.SearchPick(
                "search from deck",
                candidates,
                1,
                null,
                false,
                null
        ));
    }
}
