package com.example.dueltower.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.Map;

@Validated
@ConfigurationProperties(prefix = "duel.balance")
public class RewardTableProperties {
    @Valid
    private final Chest chest = new Chest();

    @Valid
    private final Judgement judgement = new Judgement();

    @NotEmpty
    private Map<@NotBlank String, @NotNull @PositiveOrZero Integer> sellPrices;

    public Chest getChest() {
        return chest;
    }

    public Judgement getJudgement() {
        return judgement;
    }

    public Map<@NotBlank String, @NotNull @PositiveOrZero Integer> getSellPrices() {
        return sellPrices;
    }

    public void setSellPrices(Map<@NotBlank String, @NotNull @PositiveOrZero Integer> sellPrices) {
        this.sellPrices = sellPrices;
    }

    public static class Chest {
        @NotNull
        @PositiveOrZero
        private Integer goldPerChest;

        @NotBlank
        private String itemId;

        @NotNull
        @PositiveOrZero
        private Integer itemCountPerChest;

        public Integer getGoldPerChest() {
            return goldPerChest;
        }

        public void setGoldPerChest(Integer goldPerChest) {
            this.goldPerChest = goldPerChest;
        }

        public String getItemId() {
            return itemId;
        }

        public void setItemId(String itemId) {
            this.itemId = itemId;
        }

        public Integer getItemCountPerChest() {
            return itemCountPerChest;
        }

        public void setItemCountPerChest(Integer itemCountPerChest) {
            this.itemCountPerChest = itemCountPerChest;
        }
    }

    public static class Judgement {
        @NotNull
        @PositiveOrZero
        private Integer successGold;

        @NotNull
        @PositiveOrZero
        private Integer successKeys;

        @NotNull
        @PositiveOrZero
        private Integer failureGold;

        public Integer getSuccessGold() {
            return successGold;
        }

        public void setSuccessGold(Integer successGold) {
            this.successGold = successGold;
        }

        public Integer getSuccessKeys() {
            return successKeys;
        }

        public void setSuccessKeys(Integer successKeys) {
            this.successKeys = successKeys;
        }

        public Integer getFailureGold() {
            return failureGold;
        }

        public void setFailureGold(Integer failureGold) {
            this.failureGold = failureGold;
        }
    }
}
