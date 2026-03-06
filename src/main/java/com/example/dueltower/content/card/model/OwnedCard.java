package com.example.dueltower.content.card.model;

/**
 * 플레이어 보유 카드 슬롯.
 * weakened=true 인 카드는 덱 편성 시 잠금(선택 불가) 상태다.
 */
public record OwnedCard(
        String cardId,
        boolean weakened
) {}
