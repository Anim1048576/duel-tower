package com.example.dueltower.session.dto;

import java.util.List;

public record PlayerStateDto(
        String playerId,
        boolean ready,
        List<String> passiveIds,
        List<OwnedCardDto> ownedCards,
        List<String> deck,
        List<String> deckOwnedCardIds,
        List<String> hand,
        List<String> grave,
        List<String> field,
        List<String> excluded,
        String exCard,
        boolean exOnCooldown,
        PendingDecisionDto pendingDecision,
        boolean swappedThisTurn,
        int cardsPlayedThisTurn,
        boolean usedExThisTurn,
        int handLimit,
        int fieldLimit,
        int ownedCardCount,
        int maxOwnedCardCount,
        boolean forgettingRequired,
        List<EquippedItemDto> equippedItems,
        String controlType,
        String controllerPlayerId
) {
    public record EquippedItemDto(
            String slot,
            String inventoryEquipId,
            String equipId,
            boolean bound,
            Integer loadedAmmo,
            Integer maxLoadedAmmo,
            boolean actionAvailable
    ) {}
}
