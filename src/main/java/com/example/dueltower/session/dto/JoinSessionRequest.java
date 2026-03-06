package com.example.dueltower.session.dto;

import java.util.List;

/**
 * Join payload contract.
 * - playerId: required
 * - passiveIds: optional, 0..2 items
 * - presetDeckCardIds: optional, omitted 시 서버 기본 프리셋 덱 로드
 * - ownedCards: optional, omitted 시 서버 기본 보유 카드(20) 로드
 */
public record JoinSessionRequest(
        String playerId,
        List<String> passiveIds,
        List<String> presetDeckCardIds,
        String presetExCardId,
        List<OwnedCardDto> ownedCards
) {}
