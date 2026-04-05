package com.example.dueltower.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(GameRulesProperties.class)
public class GameRulesConfiguration {

    @Bean
    public GameRules gameRules(GameRulesProperties properties) {
        return new GameRules(
                requireConfigured(properties.getDeckSize(), "duel.game.rules.deck-size"),
                requireConfigured(properties.getMaxDeckCopies(), "duel.game.rules.max-deck-copies"),
                requireConfigured(properties.getMaxDeckEditChanges(), "duel.game.rules.max-deck-edit-changes"),
                requireConfigured(properties.getMaxPassives(), "duel.game.rules.max-passives"),
                requireConfigured(properties.getMaxOwnedCards(), "duel.game.rules.max-owned-cards"),
                requireConfigured(properties.getHandLimit(), "duel.game.rules.hand-limit"),
                requireConfigured(properties.getFieldLimit(), "duel.game.rules.field-limit"),
                requireConfigured(properties.getCombatStartDrawCount(), "duel.game.rules.combat-start-draw-count"),
                requireConfigured(properties.getTurnStartBonusDrawHandThreshold(), "duel.game.rules.turn-start-bonus-draw-hand-threshold"),
                requireConfigured(properties.getTurnStartDrawBelowThreshold(), "duel.game.rules.turn-start-draw-below-threshold"),
                requireConfigured(properties.getTurnStartDrawAtOrAboveThreshold(), "duel.game.rules.turn-start-draw-at-or-above-threshold")
        );
    }

    private static int requireConfigured(Integer value, String key) {
        if (value == null) {
            throw new IllegalStateException("missing required game rule property: " + key);
        }
        return value;
    }
}
