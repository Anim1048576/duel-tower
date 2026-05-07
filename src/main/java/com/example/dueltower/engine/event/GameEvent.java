package com.example.dueltower.engine.event;

import java.util.List;
import java.util.Map;

public sealed interface GameEvent permits
        GameEvent.LogAppended,
        GameEvent.CombatLogAppended,
        GameEvent.CardsMoved,
        GameEvent.DeckShuffled,
        GameEvent.DeckRefilled,
        GameEvent.PendingDecisionSet,
        GameEvent.PendingDecisionCleared,
        GameEvent.TurnAdvanced {

    record LogAppended(String line) implements GameEvent {}
    record CombatLogAppended(
            String type,
            String visibility,
            String message,
            String actorId,
            String actorName,
            String targetId,
            String targetName,
            String cardDefId,
            String cardName,
            List<String> details,
            Map<String, Object> data
    ) implements GameEvent {
        public CombatLogAppended {
            type = type == null || type.isBlank() ? "combat.log" : type;
            visibility = visibility == null || visibility.isBlank() ? "PLAYER" : visibility;
            message = message == null ? "" : message;
            details = details == null ? List.of() : List.copyOf(details);
            data = data == null ? Map.of() : Map.copyOf(data);
        }
    }
    record CardsMoved(String playerId, String from, String to, int count) implements GameEvent {}
    record DeckShuffled(String playerId) implements GameEvent {}
    record DeckRefilled(String playerId) implements GameEvent {}

    record PendingDecisionSet(String playerId, String type, String reason) implements GameEvent {}
    record PendingDecisionCleared(String playerId, String type) implements GameEvent {}

    record TurnAdvanced(String nextActorKey, int round) implements GameEvent {}
}
