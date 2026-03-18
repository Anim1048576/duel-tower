package com.example.dueltower.common.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiErrorResolverTest {

    @Test
    void commandRejectionMapsSearchPickValidationToUnifiedSchema() {
        ApiErrorResponse error = ApiErrorResolver.commandRejection(List.of(
                "selected count mismatch (need=2)",
                "selected id not in candidates: card-1"
        ));

        assertEquals("INVALID_SEARCH_PICK_SELECTION", error.code());
        assertEquals("RULE", error.category());
        assertEquals("선택 가능한 카드와 선택 개수를 다시 확인해 주세요.", error.userMessage());
        assertEquals(409, error.status());
    }
}
