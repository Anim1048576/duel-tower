package com.example.dueltower.engine.core;

import com.example.dueltower.engine.command.DrawCommand;
import com.example.dueltower.engine.core.combat.CombatStatuses;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ZoneOpsDeckOutTest {

    @Test
    void drawWithRefill_setsBattleIncapacitated_whenDeckAndGraveAreEmptyInCombat() {
        Fixture f = new Fixture(true);
        List<GameEvent> events = new ArrayList<>();

        ZoneOps.drawWithRefill(f.state, f.ctx, f.player, 1, events);

        assertThat(f.player.status(CombatStatuses.BATTLE_INCAPACITATED)).isEqualTo(1);
        assertThat(events)
                .filteredOn(e -> e instanceof GameEvent.LogAppended)
                .map(e -> ((GameEvent.LogAppended) e).line())
                .anyMatch(line -> line.contains("[전투 불능]"));
    }

    @Test
    void drawWithRefill_doesNotSetBattleIncapacitated_outsideCombat() {
        Fixture f = new Fixture(false);

        ZoneOps.drawWithRefill(f.state, f.ctx, f.player, 1, new ArrayList<>());

        assertThat(f.player.status(CombatStatuses.BATTLE_INCAPACITATED)).isZero();
    }

    @Test
    void drawCommandValidation_rejectsBattleIncapacitatedPlayer() {
        Fixture f = new Fixture(true);
        f.player.statusSet(CombatStatuses.BATTLE_INCAPACITATED, 1);

        DrawCommand cmd = new DrawCommand(UUID.randomUUID(), 0L, f.playerId, 1);

        assertThat(cmd.validate(f.state, f.ctx)).contains("player is battle incapacitated");
    }


    @Test
    void drawCommandValidation_rejectsZeroHpPlayerEvenWithoutStatus() {
        Fixture f = new Fixture(true);
        f.player.hp(0);
        f.player.statusSet(CombatStatuses.BATTLE_INCAPACITATED, 0);

        DrawCommand cmd = new DrawCommand(UUID.randomUUID(), 0L, f.playerId, 1);

        assertThat(cmd.validate(f.state, f.ctx)).contains("player is battle incapacitated");
    }

    private static final class Fixture {
        final GameState state = new GameState(new SessionId(UUID.randomUUID()), 1L);
        final PlayerId playerId = new PlayerId("p1");
        final PlayerState player = new PlayerState(playerId);
        final EngineContext ctx = new EngineContext(Map.of(), Map.of());

        Fixture(boolean withCombat) {
            state.players().put(playerId, player);
            if (withCombat) {
                CombatState cs = new CombatState();
                cs.phase(CombatPhase.MAIN);
                cs.turnOrder().add(TargetRef.ofPlayer(playerId));
                cs.currentTurnIndex(0);
                state.combat(cs);
            }
        }
    }
}
