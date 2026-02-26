package com.example.dueltower.content.deck.dto;

/**
 * 요청용 카드 스펙
 * - count가 null이면 1로 간주
 */
public record DeckCardSpec(String cardId, Integer count) {}
