package com.example.dueltower.session.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionRequestCompatibilityTest {

    @Test
    void requestedDeckOwnedCardIdsPrefersCanonicalField() {
        UpdateSessionDeckRequest req = new UpdateSessionDeckRequest(
                List.of("oc-1", "oc-2"),
                List.of("C001", "C002")
        );

        assertEquals(List.of("oc-1", "oc-2"), req.requestedDeckOwnedCardIds());
    }

    @Test
    void requestedPresetDeckOwnedCardIdsPrefersCanonicalField() {
        JoinSessionRequest req = new JoinSessionRequest(
                "player",
                null,
                List.of(),
                List.of("oc-10", "oc-11"),
                List.of("C001", "C002"),
                null,
                List.of()
        );

        assertEquals(List.of("oc-10", "oc-11"), req.requestedPresetDeckOwnedCardIds());
    }

    @Test
    void requestedDeckOwnedCardIdsFallsBackToLegacyDeckCardIds() {
        UpdateSessionDeckRequest req = new UpdateSessionDeckRequest(
                null,
                List.of("C001", "C001", "C002")
        );

        assertEquals(List.of("C001", "C001", "C002"), req.requestedDeckOwnedCardIds());
    }

    @Test
    void requestedPresetDeckOwnedCardIdsFallsBackToLegacyPresetDeckCardIds() {
        JoinSessionRequest req = new JoinSessionRequest(
                "player",
                null,
                List.of(),
                null,
                List.of("C001", "C002"),
                null,
                List.of()
        );

        assertEquals(List.of("C001", "C002"), req.requestedPresetDeckOwnedCardIds());
    }
}
