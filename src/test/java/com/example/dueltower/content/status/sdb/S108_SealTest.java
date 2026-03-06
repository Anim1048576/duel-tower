package com.example.dueltower.content.status.sdb;

import com.example.dueltower.engine.command.UseExCommand;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class S108_SealTest {

    @Test
    void validateUseEx_isBlockedAndClearedOnTurnEnd() {
        Fixture f = new Fixture();
        f.player.statusSet(S108_Seal.ID, 1);

        UseExCommand blocked = new UseExCommand(
                UUID.randomUUID(),
                f.state.version(),
                f.playerId,
                TargetSelection.empty()
        );

        assertThat(blocked.validate(f.state, f.ctx))
                .contains("seal: cannot use EX while sealed");

        new S108_Seal().onTurnEnd(f.rt(), TargetRef.ofPlayer(f.playerId), 1);

        UseExCommand allowed = new UseExCommand(
                UUID.randomUUID(),
                f.state.version(),
                f.playerId,
                TargetSelection.empty()
        );

        assertThat(allowed.validate(f.state, f.ctx))
                .doesNotContain("seal: cannot use EX while sealed");
    }

    private static final class Fixture {
        final GameState state = new GameState(new SessionId(UUID.randomUUID()), 1L);
        final PlayerId playerId = new PlayerId("p1");
        final PlayerState player = new PlayerState(playerId);
        final CardDefId exDefId = new CardDefId("EX_TEST");
        final CardInstId exCardId = new CardInstId(UUID.randomUUID());
        final S108_Seal seal = new S108_Seal();
        final EngineContext ctx;

        Fixture() {
            state.players().put(playerId, player);

            CombatState combat = new CombatState();
            combat.phase(CombatPhase.MAIN);
            combat.turnOrder().add(TargetRef.ofPlayer(playerId));
            combat.currentTurnIndex(0);
            combat.round(1);
            state.combat(combat);

            player.exCard(exCardId);
            player.ap(3);

            state.cards().put(exCardId, new CardInstance(exCardId, exDefId, playerId, Zone.EX));

            CardDefinition exDef = new CardDefinition(
                    exDefId,
                    "Test EX",
                    CardType.EX,
                    0,
                    Map.of(),
                    Zone.EX,
                    false,
                    ""
            );

            CardEffect exEffect = new CardEffect() {
                @Override public String id() { return exDefId.value(); }
                @Override public void resolve(com.example.dueltower.engine.core.effect.EffectContext ec) {}
            };

            this.ctx = new EngineContext(
                    Map.of(exDefId, exDef),
                    Map.of(exDefId, exEffect),
                    Map.of(S108_Seal.ID, seal.definition()),
                    Map.of(S108_Seal.ID, seal)
            );
        }

        com.example.dueltower.engine.core.effect.status.StatusRuntime rt() {
            return new com.example.dueltower.engine.core.effect.status.StatusRuntime(
                    state,
                    ctx,
                    new java.util.ArrayList<>(),
                    "TEST"
            );
        }
    }
}
