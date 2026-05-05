package com.example.dueltower.session.dto;

import java.util.List;

/**
 * Join payload contract.
 * - playerId: required
 * - passiveIds: optional, 0..2 items
 * - deckOwnedCardIds: optional, omitted 시 서버 기본 덱 로드
 * - deckCardIds: optional legacy compatibility field
 * - ownedCards: optional, omitted 시 서버 기본 보유 카드(20) 로드
 */
public record JoinSessionRequest(
        String playerId,
        Long characterId,
        List<String> passiveIds,
        /** Canonical request field. */
        List<String> deckOwnedCardIds,
        /** Legacy compatibility field; prefer deckOwnedCardIds when both are provided. */
        List<String> deckCardIds,
        String exCardId,
        List<OwnedCardDto> ownedCards
) {
    public List<String> requestedDeckOwnedCardIds() {
        if (deckOwnedCardIds != null) {
            return deckOwnedCardIds;
        }
        return deckCardIds;
    }
}
