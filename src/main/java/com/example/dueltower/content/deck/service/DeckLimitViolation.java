package com.example.dueltower.content.deck.service;

import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

final class DeckLimitViolation {
    private DeckLimitViolation() {
    }

    static ResponseStatusException playerDeckMustHaveExact(int actual, int expected) {
        return new ResponseStatusException(
                BAD_REQUEST,
                "player deck must have exactly " + expected + " cards (got " + actual + ")"
        );
    }

    static ResponseStatusException playerDeckCannotExceedTotal(int actual, int max) {
        return new ResponseStatusException(
                BAD_REQUEST,
                "player deck cannot exceed " + max + " cards (got " + actual + ")"
        );
    }

    static ResponseStatusException playerDeckCardCopyExceeded(String cardId, int actual, int max) {
        return new ResponseStatusException(
                BAD_REQUEST,
                "player deck: max " + max + " copies per card (" + cardId + "=" + actual + ")"
        );
    }
}
