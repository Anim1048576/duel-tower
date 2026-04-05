package com.example.dueltower.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GameRulesConfiguration {

    @Bean
    public GameRules gameRules(
            @Value("${duel.game.rules.deck-size:12}") int deckSize,
            @Value("${duel.game.rules.max-deck-copies:3}") int maxDeckCopies,
            @Value("${duel.game.rules.max-deck-edit-changes:2}") int maxDeckEditChanges,
            @Value("${duel.game.rules.max-passives:2}") int maxPassives,
            @Value("${duel.game.rules.max-owned-cards:20}") int maxOwnedCards,
            @Value("${duel.game.rules.hand-limit:6}") int handLimit,
            @Value("${duel.game.rules.field-limit:5}") int fieldLimit,
            @Value("${duel.game.rules.combat-start-draw-count:4}") int combatStartDrawCount,
            @Value("${duel.game.rules.turn-start-bonus-draw-hand-threshold:4}") int turnStartBonusDrawHandThreshold,
            @Value("${duel.game.rules.turn-start-draw-below-threshold:2}") int turnStartDrawBelowThreshold,
            @Value("${duel.game.rules.turn-start-draw-at-or-above-threshold:1}") int turnStartDrawAtOrAboveThreshold
    ) {
        return new GameRules(
                deckSize,
                maxDeckCopies,
                maxDeckEditChanges,
                maxPassives,
                maxOwnedCards,
                handLimit,
                fieldLimit,
                combatStartDrawCount,
                turnStartBonusDrawHandThreshold,
                turnStartDrawBelowThreshold,
                turnStartDrawAtOrAboveThreshold
        );
    }
}
