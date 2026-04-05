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
        return new RewardTableConfig(
                new RewardTableConfig.ChestReward(
                        chest.getGoldPerChest(),
                        chest.getItemId(),
                        chest.getItemCountPerChest()
                ),
                new RewardTableConfig.JudgementReward(
                        judgement.getSuccessGold(),
                        judgement.getSuccessKeys(),
                        judgement.getFailureGold()
                ),
                properties.getSellPrices()
        );
    }
}
