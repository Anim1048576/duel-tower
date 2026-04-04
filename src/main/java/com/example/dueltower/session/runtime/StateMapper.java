package com.example.dueltower.session.runtime;

import com.example.dueltower.content.card.model.OwnedCardModifier;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.session.dto.*;
import com.example.dueltower.session.service.OwnedCardForgetPolicy;

import java.util.*;

public final class StateMapper {
    private StateMapper() {}

    public static SessionStateDto toDto(String sessionCode, GameState state) {
        int currentRound = (state.combat() == null) ? 0 : state.combat().round();
        Map<String, PlayerStateDto> players = new LinkedHashMap<>();
        for (Map.Entry<Ids.PlayerId, PlayerState> e : state.players().entrySet()) {
            players.put(e.getKey().value(), toDto(e.getValue(), currentRound, state));
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
                            Map.copyOf(ci.counters()),
                            ci.sourceOwnedCardId(),
                            mapModifiers(ci.modifiers())
                    )
            );
        }

        CombatStateDto combat = null;
        if (state.combat() != null) {
            CombatState cs = state.combat();
            List<String> order = cs.turnOrder().stream()
                    .map(CombatState::actorKey)
                    .toList();
            List<CombatStateDto.SummonDto> summons = state.summons().values().stream()
                    .map(s -> new CombatStateDto.SummonDto(
                            s.id().value().toString(),
                            s.owner().value(),
                            s.hp(),
                            s.atk(),
                            s.heal(),
                            !s.actionUsedThisTurn()
                    ))
                    .toList();
            List<CombatStateDto.EnemyCombatDto> enemies = state.enemies().values().stream()
                    .map(e -> new CombatStateDto.EnemyCombatDto(
                            e.enemyId().value(),
                            e.hp(),
                            e.maxHp(),
                            e.ap(),
                            e.attackPower(),
                            e.healPower(),
                            e.exCard() == null ? null : e.exCard().value().toString(),
                            e.exActivatable(),
                            e.exOnCooldown(cs.round()),
                            Map.copyOf(e.statusValues())
                    ))
                    .toList();

            combat = new CombatStateDto(
                    cs.round(),
                    order,
                    cs.currentTurnIndex(),
                    CombatState.actorKey(cs.currentTurnActor()),
                    cs.phase().name(),
                    Map.copyOf(cs.initiatives()),
                    List.copyOf(cs.initiativeTieGroups()),
                    summons,
                    enemies
            );
        }

        return new SessionStateDto(
                sessionCode,
                state.sessionId().value().toString(),
                state.version(),
                state.seed(),
                state.nodeState().name(),
                players,
                combat,
                cards,
                toRunDto(state.runState())
        );
    }

    private static PlayerStateDto toDto(PlayerState ps, int currentRound, GameState state) {
        PendingDecisionDto pending = null;
        if (ps.pendingDecision() instanceof PendingDecision.DiscardToHandLimit dt) {
            pending = new PendingDecisionDto("DISCARD_TO_HAND_LIMIT", dt.reason(), dt.limit(), null, null, null, null, null, null);
        } else if (ps.pendingDecision() instanceof PendingDecision.SearchPick sp) {
            pending = new PendingDecisionDto(
                    "SEARCH_PICK",
                    sp.reason(),
                    null,
                    sp.pickCount(),
                    sp.candidateIds().stream().map(id -> id.value().toString()).toList(),
                    sp.destination().name(),
                    sp.shuffleAfterPick(),
                    null,
                    null
            );
        } else if (ps.pendingDecision() instanceof PendingDecision.InitiativeTieOrder it) {
            pending = new PendingDecisionDto("INITIATIVE_TIE_ORDER", it.reason(), null, null, null, null, null, it.groupIndex(), List.copyOf(it.actorKeys()));
        } else if (ps.pendingDecision() instanceof PendingDecision.JudgementChoice jc) {
            pending = new PendingDecisionDto("JUDGEMENT", jc.reason(), null, null, List.copyOf(jc.choiceIds()), null, null, null, null);
        }

        return new PlayerStateDto(
                ps.playerId().value(),
                ps.ready(),
                ps.passiveIds(),
                mapOwnedCards(ps, state),
                ps.deck().stream().map(id -> id.value().toString()).toList(),
                currentDeckOwnedCardIds(ps),
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
                ps.fieldLimit(),
                ps.ownedCardCount(),
                ps.maxOwnedCardCount(),
                ps.forgettingRequired()
        );
    }


    private static List<String> currentDeckOwnedCardIds(PlayerState ps) {
        return ps.deckOwnedCardIds();
    }

    private static List<OwnedCardDto> mapOwnedCards(PlayerState ps, GameState state) {
        Map<String, Integer> ownedCounts = new LinkedHashMap<>();
        for (var owned : ps.ownedCards()) {
            ownedCounts.merge(owned.cardId(), 1, Integer::sum);
        }

        Map<String, Integer> deckCounts = new LinkedHashMap<>();
        Set<String> deckOwnedCardIds = new LinkedHashSet<>(ps.deckOwnedCardIds());
        Map<String, String> cardIdByOwnedCardId = new LinkedHashMap<>();
        for (var owned : ps.ownedCards()) {
            cardIdByOwnedCardId.put(owned.ownedCardId(), owned.cardId());
        }
        for (String ownedCardId : ps.deckOwnedCardIds()) {
            String cardId = cardIdByOwnedCardId.get(ownedCardId);
            if (cardId != null) {
                deckCounts.merge(cardId, 1, Integer::sum);
            }
        }

        return ps.ownedCards().stream()
                .map(c -> {
                    OwnedCardForgetPolicy.ForgetCheck forgetCheck = OwnedCardForgetPolicy.evaluateWithDeckMembership(
                            c,
                            ownedCounts,
                            deckCounts,
                            deckOwnedCardIds.contains(c.ownedCardId())
                    );
                    return new OwnedCardDto(
                            c.ownedCardId(),
                            c.cardId(),
                            mapModifiers(c.modifiers()),
                            c.strengthened(),
                            c.weakened(),
                            c.lockedInDeck(),
                            forgetCheck.forgettable(),
                            forgetCheck.reason()
                    );
                })
                .toList();
    }

    private static List<OwnedCardModifierDto> mapModifiers(List<OwnedCardModifier> modifiers) {
        if (modifiers == null || modifiers.isEmpty()) {
            return List.of();
        }
        return modifiers.stream()
                .map(modifier -> new OwnedCardModifierDto(modifier.modifierId(), modifier.value()))
                .toList();
    }


    private static RunStateDto toRunDto(RunState run) {
        RunStateDto.CurrentNodeDto currentNode = toCurrentNodeDto(run);

        List<RunStateDto.NodeChoiceDto> choices = run.availableChoices().stream()
                .map(choice -> new RunStateDto.NodeChoiceDto(
                        choice.id(),
                        choice.name(),
                        choice.typeLabel(),
                        choice.rule(),
                        choice.phase().name(),
                        choice.danger().name(),
                        choice.disabled(),
                        choice.disabledReason()
                ))
                .toList();

        List<RunStateDto.RecentResultDto> recentResults = toRecentResultDtos(run);

        List<RunStateDto.InventoryItemDto> items = run.inventory().items().stream()
                .map(item -> new RunStateDto.InventoryItemDto(
                        item.id(),
                        item.name(),
                        item.count(),
                        item.bound(),
                        item.battleUsable(),
                        item.summary(),
                        item.description(),
                        item.tags()
                ))
                .toList();

        RunStateDto.InventoryDto inventory = new RunStateDto.InventoryDto(
                run.inventory().keys(),
                run.inventory().chests(),
                run.inventory().gold(),
                items
        );

        return new RunStateDto(
                run.floor(),
                run.status().name(),
                run.resultPending(),
                currentNode,
                choices,
                recentResults,
                inventory
        );
    }

    public static RunStateDto.CurrentNodeDto toCurrentNodeDto(RunState run) {
        if (run == null || run.currentNode() == null) {
            return null;
        }
        return new RunStateDto.CurrentNodeDto(
                run.currentNode().id(),
                run.currentNode().name(),
                run.currentNode().typeLabel(),
                run.currentNode().phase().name(),
                run.currentNode().danger().name(),
                run.currentNode().floor()
        );
    }

    public static List<RunStateDto.RecentResultDto> toRecentResultDtos(RunState run) {
        if (run == null) {
            return List.of();
        }
        return run.recentResults().stream()
                .map(result -> new RunStateDto.RecentResultDto(
                        result.id(),
                        result.type(),
                        result.title(),
                        result.summary(),
                        result.detail(),
                        result.source(),
                        result.at()
                ))
                .toList();
    }

    public static List<EventDto> toEventDtos(List<GameEvent> events) {
        List<EventDto> out = new ArrayList<>(events.size());
        for (GameEvent ev : events) out.add(toEventDto(ev));
        return out;
    }

    public static EventDto toEventDto(GameEvent ev) {
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
                    "nextActorKey", e.nextActorKey(),
                    // backward-compatible alias for clients that still read nextPlayerId
                    "nextPlayerId", e.nextActorKey(),
                    "round", e.round()
            ));
        }
        return new EventDto("UNKNOWN", Map.of("raw", ev.toString()));
    }
}
