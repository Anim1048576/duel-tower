package com.example.dueltower.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

@ConfigurationProperties(prefix = "duel.balance")
public class RewardTableProperties {
    private final Chest chest = new Chest();
    private final Judgement judgement = new Judgement();
    private final Map<String, Integer> sellPrices = new LinkedHashMap<>(RewardTableConfig.defaults().sellPrices());

    public Chest getChest() {
        return chest;
    }

    public Judgement getJudgement() {
        return judgement;
    }

    public Map<String, Integer> getSellPrices() {
        return sellPrices;
    }

    public static class Chest {
        private int goldPerChest = 150;
        private String itemId = "I-1";
        private int itemCountPerChest = 1;

        public int getGoldPerChest() {
            return goldPerChest;
        }

        public void setGoldPerChest(int goldPerChest) {
            this.goldPerChest = goldPerChest;
        }

        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        public int getItemCountPerChest() {
            return itemCountPerChest;
        }

        public void setItemCountPerChest(int itemCountPerChest) {
            this.itemCountPerChest = itemCountPerChest;
        }
    }

    public static class Judgement {
        private int successGold = 200;
        private int successKeys = 1;
        private int failureGold = 80;

        public int getSuccessGold() {
            return successGold;
        }

        public void setSuccessGold(int successGold) {
            this.successGold = successGold;
        }

        public int getSuccessKeys() {
            return successKeys;
        }

        public void setSuccessKeys(int successKeys) {
            this.successKeys = successKeys;
        }

        public int getFailureGold() {
            return failureGold;
        }

        public void setFailureGold(int failureGold) {
            this.failureGold = failureGold;
        }
    }
}
