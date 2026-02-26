package com.example.dueltower.session;

import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.session.dto.*;

import java.util.*;

public final class StateMapper {
    private StateMapper() {}

    public static SessionStateDto toDto(String sessionCode, GameState state) {
        int currentRound = (state.combat() == null) ? 0 : state.combat().round();
        Map<String, PlayerStateDto> players = new LinkedHashMap<>();
        for (Map.Entry<Ids.PlayerId, PlayerState> e : state.players().entrySet()) {
            players.put(e.getKey().value(), toDto(e.getValue(), currentRound));
        }

        Map<String, CardInstanceDto> cards = new HashMap<>();
        for (Map.Entry<Ids.CardInstId, CardInstance> e : state.cardInstances().entrySet()) {
            CardInstance ci = e.getValue();
            cards.put(e.getKey().value().toString(),
                    new CardInstanceDto(
                            ci.instanceId().value().toString(),
                            ci.defId().value(),
                            ci.ownerId().value(),
                            ci.zone().name(),
                            Map.copyOf(ci.counters())
                    )
            );
        }

        CombatStateDto combat = null;
        if (state.combat() != null) {
            CombatState cs = state.combat();
            List<String> order = cs.turnOrder().stream()
                    .map(CombatState::actorKey)
                    .toList();

            combat = new CombatStateDto(
                    cs.round(),
                    order,
                    cs.currentTurnIndex(),
                    CombatState.actorKey(cs.currentTurnActor()),
                    Map.copyOf(cs.initiatives()),
                    List.copyOf(cs.initiativeTieGroups())
            );
        }

        return new SessionStateDto(
                sessionCode,
                state.sessionId().value().toString(),
                state.version(),
                state.seed(),
                players,
                combat,
                cards
        );
    }

    private static PlayerStateDto toDto(PlayerState ps, int currentRound) {
        PendingDecisionDto pending = null;
        if (ps.pendingDecision() instanceof PendingDecision.DiscardToHandLimit dt) {
            pending = new PendingDecisionDto("DISCARD_TO_HAND_LIMIT", dt.reason(), dt.limit(), null);
        } else if (ps.pendingDecision() instanceof PendingDecision.SearchPick sp) {
            pending = new PendingDecisionDto("SEARCH_PICK", sp.reason(), null, sp.pickCount());
        }

        return new PlayerStateDto(
                ps.playerId().value(),
                ps.deck().stream().map(id -> id.value().toString()).toList(),
                ps.hand().stream().map(id -> id.value().toString()).toList(),
                ps.grave().stream().map(id -> id.value().toString()).toList(),
                ps.field().stream().map(id -> id.value().toString()).toList(),
                ps.excluded().stream().map(id -> id.value().toString()).toList(),
                ps.exCard() == null ? null : ps.exCard().value().toString(),
                ps.exOnCooldown(currentRound),
                pending,
                ps.swappedThisTurn(),
                ps.cardsPlayedThisTurn(),
                ps.usedExThisTurn(),
                ps.handLimit(),
                ps.fieldLimit()
        );
    }

    public static List<EventDto> toEventDtos(List<GameEvent> events) {
        List<EventDto> out = new ArrayList<>(events.size());
        for (GameEvent ev : events) out.add(toEventDto(ev));
        return out;
    }

    private static EventDto toEventDto(GameEvent ev) {
        if (ev instanceof GameEvent.LogAppended e) {
            return new EventDto("LOG_APPENDED", Map.of("line", e.line()));
        }
        if (ev instanceof GameEvent.CardsMoved e) {
            return new EventDto("CARDS_MOVED", Map.of(
                    "playerId", e.playerId(),
                    "from", e.from(),
                    "to", e.to(),
                    "count", e.count()
            ));
        }
        if (ev instanceof GameEvent.DeckShuffled e) {
            return new EventDto("DECK_SHUFFLED", Map.of("playerId", e.playerId()));
        }
        if (ev instanceof GameEvent.DeckRefilled e) {
            return new EventDto("DECK_REFILLED", Map.of("playerId", e.playerId()));
        }
        if (ev instanceof GameEvent.PendingDecisionSet e) {
            return new EventDto("PENDING_DECISION_SET", Map.of(
                    "playerId", e.playerId(),
                    "decisionType", e.type(),
                    "reason", e.reason()
            ));
        }
        if (ev instanceof GameEvent.PendingDecisionCleared e) {
            return new EventDto("PENDING_DECISION_CLEARED", Map.of(
                    "playerId", e.playerId(),
                    "decisionType", e.type()
            ));
        }
        if (ev instanceof GameEvent.TurnAdvanced e) {
            return new EventDto("TURN_ADVANCED", Map.of(
                    "nextPlayerId", e.nextPlayerId(),
                    "round", e.round()
            ));
        }
        return new EventDto("UNKNOWN", Map.of("raw", ev.toString()));
    }
}