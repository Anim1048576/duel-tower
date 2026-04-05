package com.example.dueltower.config;

import java.util.LinkedHashMap;
import java.util.Map;

public record RewardTableConfig(
        ChestReward chest,
        JudgementReward judgement,
        Map<String, Integer> sellPrices
) {
    public RewardTableConfig {
        if (chest == null) {
            throw new IllegalStateException("reward chest config is missing");
        }
        if (judgement == null) {
            throw new IllegalStateException("reward judgement config is missing");
        }
        if (sellPrices == null) {
            throw new IllegalStateException("reward sellPrices config is missing");
        }
        sellPrices = Map.copyOf(sellPrices);
    }

    public static RewardTableConfig defaults() {
        return new RewardTableConfig(
                new ChestReward(150, "I-1", 1),
                new JudgementReward(200, 1, 80),
                new LinkedHashMap<>(Map.of(
                        "I-1", 25,
                        "I-2", 100,
                        "I-3", 250,
                        "I-4", 25,
                        "I-5", 100,
                        "I-6", 125,
                        "I-7", 250,
                        "I-8", 12,
                        "E-1", 100,
                        "E-2", 125
                ))
        );
    }

    public int sellUnitPrice(String id) {
        if (id == null || id.isBlank()) {
            return 0;
        }
        return sellPrices.getOrDefault(id.trim(), 0);
    }

    public record ChestReward(
            int goldPerChest,
            String itemId,
            int itemCountPerChest
    ) {
        public ChestReward {
            if (goldPerChest < 0) {
                throw new IllegalStateException("reward chest goldPerChest must be >= 0");
            }
            if (itemId == null || itemId.isBlank()) {
                throw new IllegalStateException("reward chest itemId must not be blank");
            }
            if (itemCountPerChest < 0) {
                throw new IllegalStateException("reward chest itemCountPerChest must be >= 0");
            }
            itemId = itemId.trim();
        }
    }

    public record JudgementReward(
            int successGold,
            int successKeys,
            int failureGold
    ) {
        public JudgementReward {
            if (successGold < 0) {
                throw new IllegalStateException("reward judgement successGold must be >= 0");
            }
            if (successKeys < 0) {
                throw new IllegalStateException("reward judgement successKeys must be >= 0");
            }
            if (failureGold < 0) {
                throw new IllegalStateException("reward judgement failureGold must be >= 0");
            }
        }
    }
}
