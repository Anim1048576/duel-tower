package com.example.dueltower.engine.event;

public sealed interface GameEvent permits
        GameEvent.LogAppended,
        GameEvent.CardsMoved,
        GameEvent.DeckShuffled,
        GameEvent.DeckRefilled,
        GameEvent.PendingDecisionSet,
        GameEvent.PendingDecisionCleared,
        GameEvent.TurnAdvanced {

    record LogAppended(String line) implements GameEvent {}
    record CardsMoved(String playerId, String from, String to, int count) implements GameEvent {}
    record DeckShuffled(String playerId) implements GameEvent {}
    record DeckRefilled(String playerId) implements GameEvent {}

    record PendingDecisionSet(String playerId, String type, String reason) implements GameEvent {}
    record PendingDecisionCleared(String playerId, String type) implements GameEvent {}

    record TurnAdvanced(String nextPlayerId, int round) implements GameEvent {}
}