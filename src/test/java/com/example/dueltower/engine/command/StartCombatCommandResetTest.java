package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.model.*;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StartCombatCommandResetTest {

    @Test
    void restartsCombatFromEndedCombatAndResetsPlayerCombatState() {
        GameEngine engine = new GameEngine();
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 1234L);
        EngineContext ctx = new EngineContext(Map.of(), Map.of());

        Ids.PlayerId playerId = new Ids.PlayerId("p1");
        PlayerState player = new PlayerState(playerId);
        state.players().put(playerId, player);
        state.enemies().put(new Ids.EnemyId("e1"), new EnemyState(new Ids.EnemyId("e1"), 10));

        Ids.CardInstId deckCard = addCard(state, playerId, Zone.DECK);
        Ids.CardInstId handCard = addCard(state, playerId, Zone.HAND);
        Ids.CardInstId graveCard = addCard(state, playerId, Zone.GRAVE);
        Ids.CardInstId fieldCard = addCard(state, playerId, Zone.FIELD);
        Ids.CardInstId excludedCard = addCard(state, playerId, Zone.EXCLUDED);
        Ids.CardInstId exCard = addCard(state, playerId, Zone.EX);

        player.deck().addLast(deckCard);
        player.hand().add(handCard);
        player.grave().add(graveCard);
        player.field().add(fieldCard);
        player.excluded().add(excludedCard);
        player.exCard(exCard);

        Ids.SummonInstId summonId = new Ids.SummonInstId(UUID.randomUUID());
        state.summons().put(summonId, new SummonState(summonId, playerId, fieldCard, 3, 3, 1, 0, 1, false));
        player.activeSummons().add(summonId);
        player.summonByCard().put(fieldCard, summonId);

        player.ap(0);
        player.pendingDecision(new PendingDecision.DiscardToHandLimit("test", 1));
        player.swappedThisTurn(true);
        player.cardsPlayedThisTurn(2);
        player.usedExThisTurn(true);
        player.usedTenacityThisTurn(true);
        player.tenacityDebtThisTurn(2);
        player.exCooldownUntilRound(5);
        player.exActivatable(false);
        player.statusSet("S999", 3);
        player.statusSet(com.example.dueltower.engine.core.combat.CombatStatuses.BATTLE_INCAPACITATED, 1);

        CombatState endedCombat = new CombatState();
        endedCombat.phase(CombatPhase.END);
        state.combat(endedCombat);

        EngineResult result = engine.process(
                state,
                ctx,
                new StartCombatCommand(UUID.randomUUID(), state.version(), playerId)
        );

        assertThat(result.accepted()).isTrue();
        assertThat(state.combat()).isNotNull();
        assertThat(state.combat().phase()).isNotEqualTo(CombatPhase.END);

        assertThat(player.hand()).hasSize(4);
        assertThat(player.grave()).isEmpty();
        assertThat(player.field()).isEmpty();
        assertThat(player.excluded()).isEmpty();
        assertThat(player.deck()).hasSize(1);

        assertThat(player.pendingDecision()).isNull();
        assertThat(player.swappedThisTurn()).isFalse();
        assertThat(player.cardsPlayedThisTurn()).isZero();
        assertThat(player.usedExThisTurn()).isFalse();
        assertThat(player.usedTenacityThisTurn()).isFalse();
        assertThat(player.tenacityDebtThisTurn()).isZero();
        assertThat(player.exCooldownUntilRound()).isZero();
        assertThat(player.exActivatable()).isTrue();
        assertThat(player.ap()).isEqualTo(player.maxAp());
        assertThat(player.statusValues()).isEmpty();

        assertThat(player.activeSummons()).isEmpty();
        assertThat(player.summonByCard()).isEmpty();
        assertThat(state.summons()).isEmpty();
    }

    private static Ids.CardInstId addCard(GameState state, Ids.PlayerId owner, Zone zone) {
        Ids.CardInstId cardId = Ids.newCardInstId();
        state.cardInstances().put(
                cardId,
                new CardInstance(cardId, new Ids.CardDefId("C001"), owner, zone)
        );
        return cardId;
    }
}
