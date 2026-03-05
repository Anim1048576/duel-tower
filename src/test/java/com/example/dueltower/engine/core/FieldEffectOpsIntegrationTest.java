package com.example.dueltower.engine.core;

import com.example.dueltower.content.keyword.kdb.K003_Installed;
import com.example.dueltower.content.keyword.kdb.K004_Summon;
import com.example.dueltower.content.status.sdb.S901_InstalledFieldBuff;
import com.example.dueltower.content.status.sdb.S902_SummonFieldAura;
import com.example.dueltower.engine.core.combat.TurnPhases;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FieldEffectOpsIntegrationTest {

    @Test
    void enterAndLeaveFieldHooksAreCalledOncePerTransition() {
        PlayerId p1 = new PlayerId("P1");
        CardInstId c1 = new CardInstId("I1");

        GameState state = new GameState(new SessionId("S1"), 1L);
        state.combat(new CombatState());
        PlayerState player = new PlayerState(p1);
        state.players().put(p1, player);

        CardDefinition def = new CardDefinition(new CardDefId("T001"), "t", CardType.SKILL, 0, Map.of(), Zone.GRAVE, false, "");
        CardInstance ci = new CardInstance(c1, def.id(), p1, Zone.HAND);
        state.cardInstances().put(c1, ci);
        player.hand().add(c1);

        CardEffect effect = new CardEffect() {
            @Override public String id() { return "T001"; }
            @Override public void resolve(EffectContext ec) {}
            @Override public void onEnterField(EffectContext ec, CardInstId sourceCardId) { ec.state().card(sourceCardId).counters().merge("enter", 1, Integer::sum); }
            @Override public void onLeaveField(EffectContext ec, CardInstId sourceCardId) { ec.state().card(sourceCardId).counters().merge("leave", 1, Integer::sum); }
        };

        EngineContext ctx = new EngineContext(Map.of(def.id(), def), Map.of(def.id(), effect), Map.of(), Map.of(), Map.of(), Map.of());
        List<com.example.dueltower.engine.event.GameEvent> out = new ArrayList<>();

        ZoneOps.moveToZoneOrVanishIfToken(state, ctx, player, c1, Zone.FIELD, out);
        ZoneOps.moveToZoneOrVanishIfToken(state, ctx, player, c1, Zone.FIELD, out); // same zone no-op for enter hook
        ZoneOps.moveToZoneOrVanishIfToken(state, ctx, player, c1, Zone.GRAVE, out);
        ZoneOps.moveToZoneOrVanishIfToken(state, ctx, player, c1, Zone.DECK, out); // already left

        assertEquals(1, ci.counters().getOrDefault("enter", 0));
        assertEquals(1, ci.counters().getOrDefault("leave", 0));
    }

    @Test
    void turnHooksRunOnceEvenWhenCardIsAlsoSummonSource() {
        PlayerId p1 = new PlayerId("P1");
        CardInstId c1 = new CardInstId("I1");

        GameState state = new GameState(new SessionId("S2"), 2L);
        state.combat(new CombatState());
        PlayerState player = new PlayerState(p1);
        state.players().put(p1, player);

        CardDefinition def = new CardDefinition(new CardDefId("T002"), "t2", CardType.SKILL, 0, Map.of(), Zone.FIELD, false, "");
        CardInstance ci = new CardInstance(c1, def.id(), p1, Zone.FIELD);
        ci.fieldEffectActive(true);
        state.cardInstances().put(c1, ci);
        player.field().add(c1);
        player.summonByCard().put(c1, new Ids.SummonInstId(c1.value()));

        CardEffect effect = new CardEffect() {
            @Override public String id() { return "T002"; }
            @Override public void resolve(EffectContext ec) {}
            @Override public void onTurnStart(EffectContext ec, CardInstId sourceCardId) { ec.state().card(sourceCardId).counters().merge("ts", 1, Integer::sum); }
            @Override public void onTurnEnd(EffectContext ec, CardInstId sourceCardId) { ec.state().card(sourceCardId).counters().merge("te", 1, Integer::sum); }
        };

        EngineContext ctx = new EngineContext(Map.of(def.id(), def), Map.of(def.id(), effect), Map.of(), Map.of(), Map.of(), Map.of());
        List<com.example.dueltower.engine.event.GameEvent> out = new ArrayList<>();

        TurnPhases.turnStart(state, ctx, TargetRef.ofPlayer(p1), out, "T");
        TurnPhases.turnEnd(state, ctx, TargetRef.ofPlayer(p1), out, "T");

        assertEquals(1, ci.counters().getOrDefault("ts", 0));
        assertEquals(1, ci.counters().getOrDefault("te", 0));
    }

    @Test
    void installedAndSummonAurasAreBoundToFieldPresence() {
        PlayerId p1 = new PlayerId("P1");
        CardInstId c1 = new CardInstId("I1");

        GameState state = new GameState(new SessionId("S3"), 3L);
        state.combat(new CombatState());
        PlayerState player = new PlayerState(p1);
        state.players().put(p1, player);

        CardDefinition def = new CardDefinition(
                new CardDefId("T003"),
                "aura",
                CardType.SKILL,
                0,
                Map.of(K003_Installed.ID, 1, K004_Summon.ID, 1),
                Zone.FIELD,
                false,
                ""
        );
        CardInstance ci = new CardInstance(c1, def.id(), p1, Zone.HAND);
        state.cardInstances().put(c1, ci);
        player.hand().add(c1);

        CardEffect effect = new CardEffect() {
            @Override public String id() { return "T003"; }
            @Override public void resolve(EffectContext ec) {}
        };

        EngineContext ctx = new EngineContext(Map.of(def.id(), def), Map.of(def.id(), effect), Map.of(), Map.of(), Map.of(), Map.of());
        List<com.example.dueltower.engine.event.GameEvent> out = new ArrayList<>();

        ZoneOps.moveToZoneOrVanishIfToken(state, ctx, player, c1, Zone.FIELD, out);
        assertEquals(1, player.statusValues().getOrDefault(S901_InstalledFieldBuff.ID, 0));
        assertEquals(1, state.combat().factionStatusValues(CombatState.FactionId.PLAYERS).getOrDefault(S902_SummonFieldAura.ID, 0));

        ZoneOps.moveToZoneOrVanishIfToken(state, ctx, player, c1, Zone.GRAVE, out);
        assertEquals(0, player.statusValues().getOrDefault(S901_InstalledFieldBuff.ID, 0));
        assertEquals(0, state.combat().factionStatusValues(CombatState.FactionId.PLAYERS).getOrDefault(S902_SummonFieldAura.ID, 0));
    }
}
