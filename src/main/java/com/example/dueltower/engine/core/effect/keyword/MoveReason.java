package com.example.dueltower.engine.core.effect.keyword;

/**
 * Reason for moving a card instance between zones.
 * Used by keyword hooks (e.g. "제외" applies on PLAY/DESTROY but not on discard).
 */
public enum MoveReason {
    PLAY,
    DESTROY,
    DISCARD,
    OTHER
}
