package com.example.dueltower.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationFailFastTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class
            ))
            .withUserConfiguration(GameRulesConfiguration.class, RewardTableConfiguration.class);

    @Test
    void gameRulesMissingRequiredFieldFailsContextStartup() {
        contextRunner
                .withPropertyValues(
                        "duel.game.rules.deck-size=12",
                        "duel.game.rules.max-deck-copies=3",
                        "duel.game.rules.max-deck-edit-changes=2",
                        "duel.game.rules.max-owned-cards=20",
                        "duel.game.rules.hand-limit=6",
                        "duel.game.rules.field-limit=5",
                        "duel.game.rules.combat-start-draw-count=4",
                        "duel.game.rules.turn-start-bonus-draw-hand-threshold=4",
                        "duel.game.rules.turn-start-draw-below-threshold=2",
                        "duel.game.rules.turn-start-draw-at-or-above-threshold=1",
                        "duel.balance.chest.gold-per-chest=150",
                        "duel.balance.chest.item-id=I-1",
                        "duel.balance.chest.item-count-per-chest=1",
                        "duel.balance.judgement.success-gold=200",
                        "duel.balance.judgement.success-keys=1",
                        "duel.balance.judgement.failure-gold=80",
                        "duel.balance.sell-prices.I-1=25"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("maxPassives");
                });
    }

    @Test
    void rewardSellPricesMissingFailsContextStartup() {
        contextRunner
                .withPropertyValues(
                        "duel.game.rules.deck-size=12",
                        "duel.game.rules.max-deck-copies=3",
                        "duel.game.rules.max-deck-edit-changes=2",
                        "duel.game.rules.max-passives=2",
                        "duel.game.rules.max-owned-cards=20",
                        "duel.game.rules.hand-limit=6",
                        "duel.game.rules.field-limit=5",
                        "duel.game.rules.combat-start-draw-count=4",
                        "duel.game.rules.turn-start-bonus-draw-hand-threshold=4",
                        "duel.game.rules.turn-start-draw-below-threshold=2",
                        "duel.game.rules.turn-start-draw-at-or-above-threshold=1",
                        "duel.game.rules.max-consumable-uses-per-turn=1",
                        "duel.game.rules.max-consumable-uses-per-combat=3",
                        "duel.balance.chest.gold-per-chest=150",
                        "duel.balance.chest.item-id=I-1",
                        "duel.balance.chest.item-count-per-chest=1",
                        "duel.balance.judgement.success-gold=200",
                        "duel.balance.judgement.success-keys=1",
                        "duel.balance.judgement.failure-gold=80"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("sellPrices");
                });
    }
}
