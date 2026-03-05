package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import com.example.dueltower.engine.model.Ids.SummonInstId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class UseSummonActionCommandTest {

    @Test
    void validateRejectsUsedActionAndInsufficientAp() {
        Fixture f = new Fixture();
        f.player.ap(1);
        f.summon.actionUsedThisTurn(true);

        UseSummonActionCommand cmd = new UseSummonActionCommand(
                UUID.randomUUID(),
                1L,
                f.playerId,
                f.summonId,
                TargetSelection.empty()
        );

        List<String> errors = cmd.validate(f.state, f.ctx);

        assertThat(errors).contains("summon action already used this turn");
        assertThat(errors).contains("not enough ap (need=2, have=1)");
    }

    @Test
    void validateRejectsNotOwnedSummon() {
        Fixture f = new Fixture();
        f.summon = new SummonState(f.summonId, new PlayerId("p2"), f.sourceCardId, 5, 5, 0, 0, 2, false);
        f.state.summons().put(f.summonId, f.summon);

        UseSummonActionCommand cmd = new UseSummonActionCommand(
                UUID.randomUUID(),
                1L,
                f.playerId,
                f.summonId,
                TargetSelection.empty()
        );

        assertThat(cmd.validate(f.state, f.ctx)).contains("summon is not yours");
    }

    @Test
    void handleConsumesApAndMarksActionUsed() {
        Fixture f = new Fixture();

        UseSummonActionCommand cmd = new UseSummonActionCommand(
                UUID.randomUUID(),
                1L,
                f.playerId,
                f.summonId,
                TargetSelection.empty()
        );

        List<GameEvent> events = cmd.handle(f.state, f.ctx);

        assertThat(f.player.ap()).isEqualTo(1);
        assertThat(f.summon.actionUsedThisTurn()).isTrue();
        assertThat(f.resolvedCount.get()).isEqualTo(1);
        assertThat(events).isNotEmpty();
    }

    private static final class Fixture {
        final GameState state = new GameState(new SessionId(UUID.randomUUID()), 1L);
        final PlayerId playerId = new PlayerId("p1");
        final PlayerState player = new PlayerState(playerId);
        final CardDefId defId = new CardDefId("C_SUMMON");
        final CardInstId sourceCardId = new CardInstId(UUID.randomUUID());
        final SummonInstId summonId = new SummonInstId(UUID.randomUUID());
        SummonState summon = new SummonState(summonId, playerId, sourceCardId, 5, 5, 0, 0, 2, false);
        final AtomicInteger resolvedCount = new AtomicInteger(0);
        final EngineContext ctx;

        Fixture() {
            state.players().put(playerId, player);
            player.ap(3);

            CombatState combat = new CombatState();
            combat.phase(CombatPhase.MAIN);
            combat.turnOrder().add(TargetRef.ofPlayer(playerId));
            combat.currentTurnIndex(0);
            state.combat(combat);

            state.cards().put(sourceCardId, new CardInstance(sourceCardId, defId, playerId, Zone.FIELD));
            state.summons().put(summonId, summon);
            player.activeSummons().add(summonId);

            CardDefinition definition = new CardDefinition(defId, "Summon", CardType.SKILL, 0, Map.of(), Zone.GRAVE, false, "");
            CardEffect effect = new CardEffect() {
                @Override
                public String id() {
                    return defId.value();
                }

                @Override
                public void resolve(EffectContext ec) {
                    resolvedCount.incrementAndGet();
                }
            };
            this.ctx = new EngineContext(Map.of(defId, definition), Map.of(defId, effect));
        }
    }
}
