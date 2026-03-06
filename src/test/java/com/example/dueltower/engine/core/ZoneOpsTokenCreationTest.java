package com.example.dueltower.engine.core;

import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ZoneOpsTokenCreationTest {

    private static final CardDefId TOKEN_DEF_ID = new CardDefId("TOKEN_TEST");

    @Test
    void createTokenInZone_rejectsWhenHandIsFull() {
        Fixture f = new Fixture();
        List<GameEvent> events = new ArrayList<>();

        for (int i = 0; i < f.player.handLimit(); i++) {
            ZoneOps.createCardInZone(f.state, f.player, TOKEN_DEF_ID, Zone.HAND);
        }

        CardInstId created = ZoneOps.createTokenInZone(f.state, f.ctx, f.player, TOKEN_DEF_ID, Zone.HAND, events);

        assertThat(created).isNull();
        assertThat(f.player.hand()).hasSize(f.player.handLimit());
        assertThat(events)
                .filteredOn(e -> e instanceof GameEvent.LogAppended)
                .map(e -> ((GameEvent.LogAppended) e).line())
                .anyMatch(line -> line.contains("token creation rejected: hand is full"));
    }

    @Test
    void createTokenInZone_createsWhenFieldHasCapacity() {
        Fixture f = new Fixture();
        List<GameEvent> events = new ArrayList<>();

        CardInstId created = ZoneOps.createTokenInZone(f.state, f.ctx, f.player, TOKEN_DEF_ID, Zone.FIELD, events);

        assertThat(created).isNotNull();
        assertThat(f.player.field()).contains(created);
        assertThat(f.state.card(created).zone()).isEqualTo(Zone.FIELD);
        assertThat(events)
                .filteredOn(e -> e instanceof GameEvent.LogAppended)
                .map(e -> ((GameEvent.LogAppended) e).line())
                .anyMatch(line -> line.contains("created token"));
    }

    private static final class Fixture {
        final GameState state = new GameState(new SessionId(UUID.randomUUID()), 1L);
        final PlayerId playerId = new PlayerId("p1");
        final PlayerState player = new PlayerState(playerId);
        final EngineContext ctx = new EngineContext(Map.of(
                TOKEN_DEF_ID,
                new CardDefinition(TOKEN_DEF_ID, "Token", CardType.SKILL, 0, Map.of(), Zone.FIELD, true, "test token")
        ), Map.of());

        Fixture() {
            state.players().put(playerId, player);
        }
    }
}
