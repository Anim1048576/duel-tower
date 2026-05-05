package com.example.dueltower.session.config;

import com.example.dueltower.config.GameRules;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StarterLoadoutConfigTest {

    @Test
    void defaults_match_existing_starter_behavior() {
        StarterLoadoutConfig config = StarterLoadoutConfig.defaults(GameRules.defaults());

        assertEquals("EX901", config.defaultExCardId());
        assertEquals(20, config.defaultOwnedCards().size());
        assertEquals(12, config.defaultDeckCardIds().size());
        assertEquals(List.of("C001", "C001", "C001"), config.defaultDeckCardIds().subList(0, 3));
    }

    @Test
    void throws_when_default_ex_card_id_is_blank() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> new StarterLoadoutConfig(
                new StarterLoadoutConfig.StarterLoadoutRaw(
                        " ",
                        List.of("C001", "C001", "C001", "C001", "C001", "C002", "C002", "C002", "C002", "C002", "C003", "C003", "C003", "C003", "C003", "C004", "C004", "C004", "C004", "C004"),
                        List.of("C001", "C001", "C001", "C002", "C002", "C002", "C003", "C003", "C003", "C004", "C004", "C004")
                ),
                GameRules.defaults()
        ));

        assertEquals("starter defaultExCardId must not be blank", exception.getMessage());
    }

    @Test
    void throws_when_default_deck_size_does_not_match_game_rules() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> new StarterLoadoutConfig(
                new StarterLoadoutConfig.StarterLoadoutRaw(
                        "EX901",
                        List.of("C001", "C001", "C001", "C001", "C001", "C002", "C002", "C002", "C002", "C002", "C003", "C003", "C003", "C003", "C003", "C004", "C004", "C004", "C004", "C004"),
                        List.of("C001")
                ),
                GameRules.defaults()
        ));

        assertEquals("starter defaultDeckCardIds size must be exactly 12", exception.getMessage());
    }
}
