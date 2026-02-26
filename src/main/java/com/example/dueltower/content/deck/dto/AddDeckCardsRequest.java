package com.example.dueltower.content.deck.dto;

import java.util.List;

/**
 * 덱에 카드를 '추가'하는 요청
 * - count가 null이면 1로 간주
 * - cards가 null이면 빈 목록으로 간주
 */
public record AddDeckCardsRequest(List<DeckCardSpec> cards) {}
