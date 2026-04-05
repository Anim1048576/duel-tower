package com.example.dueltower.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "duel.game.rules")
public class GameRulesProperties {
    @NotNull
    @Positive
    private Integer deckSize;

    @NotNull
    @Positive
    private Integer maxDeckCopies;

    @NotNull
    @Positive
    private Integer maxDeckEditChanges;

    @NotNull
    @Positive
    private Integer maxPassives;

    @NotNull
    @Positive
    private Integer maxOwnedCards;

    @NotNull
    @Positive
    private Integer handLimit;

    @NotNull
    @Positive
    private Integer fieldLimit;

    @NotNull
    @Positive
    private Integer combatStartDrawCount;

    @NotNull
    @PositiveOrZero
    private Integer turnStartBonusDrawHandThreshold;

    @NotNull
    @Positive
    private Integer turnStartDrawBelowThreshold;

    @NotNull
    @Positive
    private Integer turnStartDrawAtOrAboveThreshold;

    public Integer getDeckSize() {
        return deckSize;
    }

    public void setDeckSize(Integer deckSize) {
        this.deckSize = deckSize;
    }

    public Integer getMaxDeckCopies() {
        return maxDeckCopies;
    }

    public void setMaxDeckCopies(Integer maxDeckCopies) {
        this.maxDeckCopies = maxDeckCopies;
    }

    public Integer getMaxDeckEditChanges() {
        return maxDeckEditChanges;
    }

    public void setMaxDeckEditChanges(Integer maxDeckEditChanges) {
        this.maxDeckEditChanges = maxDeckEditChanges;
    }

    public Integer getMaxPassives() {
        return maxPassives;
    }

    public void setMaxPassives(Integer maxPassives) {
        this.maxPassives = maxPassives;
    }

    public Integer getMaxOwnedCards() {
        return maxOwnedCards;
    }

    public void setMaxOwnedCards(Integer maxOwnedCards) {
        this.maxOwnedCards = maxOwnedCards;
    }

    public Integer getHandLimit() {
        return handLimit;
    }

    public void setHandLimit(Integer handLimit) {
        this.handLimit = handLimit;
    }

    public Integer getFieldLimit() {
        return fieldLimit;
    }

    public void setFieldLimit(Integer fieldLimit) {
        this.fieldLimit = fieldLimit;
    }

    public Integer getCombatStartDrawCount() {
        return combatStartDrawCount;
    }

    public void setCombatStartDrawCount(Integer combatStartDrawCount) {
        this.combatStartDrawCount = combatStartDrawCount;
    }

    public Integer getTurnStartBonusDrawHandThreshold() {
        return turnStartBonusDrawHandThreshold;
    }

    public void setTurnStartBonusDrawHandThreshold(Integer turnStartBonusDrawHandThreshold) {
        this.turnStartBonusDrawHandThreshold = turnStartBonusDrawHandThreshold;
    }

    public Integer getTurnStartDrawBelowThreshold() {
        return turnStartDrawBelowThreshold;
    }

    public void setTurnStartDrawBelowThreshold(Integer turnStartDrawBelowThreshold) {
        this.turnStartDrawBelowThreshold = turnStartDrawBelowThreshold;
    }

    public Integer getTurnStartDrawAtOrAboveThreshold() {
        return turnStartDrawAtOrAboveThreshold;
    }

    public void setTurnStartDrawAtOrAboveThreshold(Integer turnStartDrawAtOrAboveThreshold) {
        this.turnStartDrawAtOrAboveThreshold = turnStartDrawAtOrAboveThreshold;
    }
}
