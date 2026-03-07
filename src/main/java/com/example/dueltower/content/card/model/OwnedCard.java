package com.example.dueltower.content.card.model;

/**
 * 플레이어 보유 카드 슬롯.
 * strengthened=true 는 카드가 강화된 상태를 의미한다.
 * weakened=true 는 카드가 약화된 상태를 의미한다.
 * lockedInDeck=true 는 현재 덱에서 제거할 수 없는 카드 슬롯을 의미한다.
 */
public record OwnedCard(
        String cardId,
        boolean strengthened,
        boolean weakened,
        boolean lockedInDeck
) {}
