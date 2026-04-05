package com.example.dueltower.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RewardTableProperties.class)
public class RewardTableConfiguration {

    @Bean
    public RewardTableConfig rewardTableConfig(RewardTableProperties properties) {
        RewardTableProperties.Chest chest = properties.getChest();
        RewardTableProperties.Judgement judgement = properties.getJudgement();
        if (properties.getSellPrices() == null || properties.getSellPrices().isEmpty()) {
            throw new IllegalStateException("missing required reward property: duel.balance.sell-prices");
        }
        return new RewardTableConfig(
                new RewardTableConfig.ChestReward(
                        requireConfigured(chest.getGoldPerChest(), "duel.balance.chest.gold-per-chest"),
                        requireConfigured(chest.getItemId(), "duel.balance.chest.item-id"),
                        requireConfigured(chest.getItemCountPerChest(), "duel.balance.chest.item-count-per-chest")
                ),
                new RewardTableConfig.JudgementReward(
                        requireConfigured(judgement.getSuccessGold(), "duel.balance.judgement.success-gold"),
                        requireConfigured(judgement.getSuccessKeys(), "duel.balance.judgement.success-keys"),
                        requireConfigured(judgement.getFailureGold(), "duel.balance.judgement.failure-gold")
                ),
                properties.getSellPrices()
        );
    }

    private static int requireConfigured(Integer value, String key) {
        if (value == null) {
            throw new IllegalStateException("missing required reward property: " + key);
        }
        return value;
    }

    private static String requireConfigured(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing required reward property: " + key);
        }
        return value;
    }
}
