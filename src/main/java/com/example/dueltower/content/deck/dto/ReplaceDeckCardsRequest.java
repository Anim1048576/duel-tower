package com.example.dueltower.content.deck.dto;

import java.util.List;

/**
 * 덱의 카드 구성을 전체 교체할 때 사용하는 요청
 * - count가 null이면 1로 간주
 * - cards는 필수(null 불가)
 */
public record ReplaceDeckCardsRequest(List<DeckCardSpec> cards) {}
