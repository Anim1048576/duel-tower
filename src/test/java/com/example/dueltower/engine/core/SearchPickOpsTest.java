package com.example.dueltower.engine.core;

import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SearchPickOpsTest {

    @Test
    @DisplayName("deckCandidates: 덱 순서를 유지하고 필터에 맞는 카드만 후보로 반환한다")
    void deckCandidatesKeepsDeckOrder() {
        Fixture fx = fixture();

        List<CardInstId> candidates = SearchPickOps.deckCandidates(
                fx.state,
                fx.ctx,
                fx.player,
                def -> def.type() == CardType.SKILL
        );

        assertEquals(List.of(fx.deck2, fx.deck3), candidates);
    }

    @Test
    @DisplayName("setDeckSearchPickPending: SearchPick pending + 이벤트를 설정한다")
    void setPendingDecisionAndEvent() {
        Fixture fx = fixture();
        List<GameEvent> events = new ArrayList<>();

        boolean set = SearchPickOps.setDeckSearchPickPending(
                fx.state,
                fx.ctx,
                fx.player,
                events,
                "test search",
                1,
                Zone.HAND,
                true,
                def -> def.type() == CardType.SKILL
        );

        assertTrue(set);
        assertTrue(fx.player.pendingDecision() instanceof PendingDecision.SearchPick sp
                && sp.pickCount() == 1
                && sp.destination() == Zone.HAND
                && sp.shuffleAfterPick()
                && sp.candidateIds().equals(List.of(fx.deck2, fx.deck3)));
        assertTrue(events.stream().anyMatch(it -> it instanceof GameEvent.PendingDecisionSet p
                && p.type().equals("SEARCH_PICK")
                && p.reason().equals("test search")));
    }

    @Test
    @DisplayName("setDeckSearchPickPending: 후보 부족/기존 pending이 있으면 설정하지 않는다")
    void doesNotSetWhenBlocked() {
        Fixture fx = fixture();
        List<GameEvent> events = new ArrayList<>();

        boolean notEnough = SearchPickOps.setDeckSearchPickPending(
                fx.state,
                fx.ctx,
                fx.player,
                events,
                "need two",
                2,
                Zone.HAND,
                false,
                def -> def.type() == CardType.ACTION
        );
        assertFalse(notEnough);

        fx.player.pendingDecision(new PendingDecision.DiscardToHandLimit("x", 6));
        boolean blocked = SearchPickOps.setDeckSearchPickPending(
                fx.state,
                fx.ctx,
                fx.player,
                events,
                "blocked",
                1,
                Zone.HAND,
                false,
                def -> true
        );
        assertFalse(blocked);
    }

    private static Fixture fixture() {
        GameState state = new GameState(new SessionId(UUID.randomUUID()), 777L);
        PlayerId playerId = new PlayerId("P1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);

        CardDefId actionDefId = new CardDefId("A001");
        CardDefId skillDefId = new CardDefId("S001");
        EngineContext ctx = new EngineContext(Map.of(
                actionDefId, new CardDefinition(actionDefId, "action", CardType.ACTION, 1, Map.of(), Zone.GRAVE, false, ""),
                skillDefId, new CardDefinition(skillDefId, "skill", CardType.SKILL, 1, Map.of(), Zone.GRAVE, false, "")
        ), Map.of());

        CardInstId deck1 = Ids.newCardInstId();
        CardInstId deck2 = Ids.newCardInstId();
        CardInstId deck3 = Ids.newCardInstId();
        state.cardInstances().put(deck1, new CardInstance(deck1, actionDefId, playerId, Zone.DECK));
        state.cardInstances().put(deck2, new CardInstance(deck2, skillDefId, playerId, Zone.DECK));
        state.cardInstances().put(deck3, new CardInstance(deck3, skillDefId, playerId, Zone.DECK));
        player.deck().addLast(deck1);
        player.deck().addLast(deck2);
        player.deck().addLast(deck3);

        return new Fixture(state, ctx, player, deck1, deck2, deck3);
    }

    private record Fixture(
            GameState state,
            EngineContext ctx,
            PlayerState player,
            CardInstId deck1,
            CardInstId deck2,
            CardInstId deck3
    ) {}
}
