package com.example.dueltower.content.deck.dto;

/**
 * 덱 검증 이슈
 * - code: 클라이언트 분기용 간단 코드
 * - field: cards/cardId/count 등 선택적 힌트
 */
public record DeckValidationIssue(
        String code,
        String message,
        String field
) {}
