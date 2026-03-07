package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResolveSearchPickCommandTest {

    @Test
    @DisplayName("validate: 선택 카드에 중복/후보 외 ID가 있으면 거부한다")
    void validateRejectsDuplicateAndOutOfCandidateIds() {
        Fixture fx = fixture();

        ResolveSearchPickCommand command = new ResolveSearchPickCommand(
                UUID.randomUUID(),
                0,
                fx.playerId,
                List.of(fx.candidate1, fx.candidate1)
        );

        List<String> errors = command.validate(fx.state, fx.ctx);
        assertTrue(errors.stream().anyMatch(it -> it.contains("duplicate selected id")));

        ResolveSearchPickCommand command2 = new ResolveSearchPickCommand(
                UUID.randomUUID(),
                0,
                fx.playerId,
                List.of(Ids.newCardInstId())
        );
        List<String> errors2 = command2.validate(fx.state, fx.ctx);
        assertTrue(errors2.stream().anyMatch(it -> it.contains("selected id not in candidates")));
    }

    @Test
    @DisplayName("validate: 잘못된 플레이어/결정 타입 불일치 시 거부한다")
    void validateRejectsWrongPlayerAndMismatchedDecisionType() {
        Fixture fx = fixture();

        ResolveSearchPickCommand wrongPlayer = new ResolveSearchPickCommand(
                UUID.randomUUID(),
                0,
                new PlayerId("P2"),
                List.of(fx.candidate1)
        );
        assertTrue(wrongPlayer.validate(fx.state, fx.ctx).contains("player not found"));

        fx.player.pendingDecision(new PendingDecision.DiscardToHandLimit("limit", 5));
        ResolveSearchPickCommand mismatch = new ResolveSearchPickCommand(
                UUID.randomUUID(),
                0,
                fx.playerId,
                List.of(fx.candidate1)
        );
        assertTrue(mismatch.validate(fx.state, fx.ctx).contains("pending decision mismatch"));
    }

    @Test
    @DisplayName("handle: 선택 카드를 목적지로 이동하고 셔플/pending clear 이벤트를 기록한다")
    void handleMovesCardsAndClearsPendingDecision() {
        Fixture fx = fixture();
        ResolveSearchPickCommand command = new ResolveSearchPickCommand(
                UUID.randomUUID(),
                0,
                fx.playerId,
                List.of(fx.candidate1)
        );

        List<String> errors = command.validate(fx.state, fx.ctx);
        assertTrue(errors.isEmpty());

        List<GameEvent> events = command.handle(fx.state, fx.ctx);

        assertEquals(Zone.HAND, fx.state.card(fx.candidate1).zone());
        assertTrue(fx.player.hand().contains(fx.candidate1));
        assertNull(fx.player.pendingDecision());
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.PendingDecisionCleared p
                && p.type().equals("SEARCH_PICK")));
        assertTrue(events.stream().anyMatch(e -> e instanceof GameEvent.DeckShuffled));
    }

    private static Fixture fixture() {
        GameState state = new GameState(new SessionId(UUID.randomUUID()), 123L);
        PlayerId playerId = new PlayerId("P1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);

        CardDefId defId = new CardDefId("C001");
        CardDefinition def = new CardDefinition(defId, "test", CardType.ACTION, 1, Map.of(), Zone.GRAVE, false, "");
        EngineContext ctx = new EngineContext(Map.of(defId, def), Map.of());

        CardInstId candidate1 = Ids.newCardInstId();
        CardInstId candidate2 = Ids.newCardInstId();
        state.cardInstances().put(candidate1, new CardInstance(candidate1, defId, playerId, Zone.DECK));
        state.cardInstances().put(candidate2, new CardInstance(candidate2, defId, playerId, Zone.DECK));
        player.deck().addLast(candidate1);
        player.deck().addLast(candidate2);

        player.pendingDecision(new PendingDecision.SearchPick(
                "search",
                List.of(candidate1, candidate2),
                1,
                Zone.HAND,
                true,
                UUID.randomUUID()
        ));

        return new Fixture(state, ctx, playerId, player, candidate1, candidate2);
    }

    private record Fixture(
            GameState state,
            EngineContext ctx,
            PlayerId playerId,
            PlayerState player,
            CardInstId candidate1,
            CardInstId candidate2
    ) {}
}
