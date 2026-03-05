package com.example.dueltower.engine.core.combat;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.core.effect.status.StatusEffect;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SummonCombatOpsTest {

    @Test
    void damageAndHealCanTargetSummonAndDestroyWhenHpReachesZero() {
        PlayerId playerId = new PlayerId("P1");
        EnemyId enemyId = new EnemyId("E1");
        CardDefId defId = new CardDefId("C_SUMMON");
        CardInstId cardId = new CardInstId("I_SUMMON");
        SummonInstId summonId = new SummonInstId(cardId.value());

        GameState state = new GameState(new SessionId(UUID.randomUUID()), 1L);
        PlayerState player = new PlayerState(playerId);
        EnemyState enemy = new EnemyState(enemyId, 30);
        state.players().put(playerId, player);
        state.enemies().put(enemyId, enemy);

        CardInstance sourceCard = new CardInstance(cardId, defId, playerId, Zone.FIELD);
        state.cardInstances().put(cardId, sourceCard);
        player.field().add(cardId);

        SummonState summon = new SummonState(summonId, playerId, cardId, 5, 5, 0, 0, 0, false);
        state.summons().put(summonId, summon);
        player.activeSummons().add(summonId);
        player.summonByCard().put(cardId, summonId);

        EngineContext ctx = new EngineContext(
                Map.of(defId, new CardDefinition(defId, "summon", CardType.SKILL, 0, Map.of(), Zone.GRAVE, false, "")),
                Map.of()
        );
        List<GameEvent> out = new ArrayList<>();

        DamageOps.apply(state, ctx, out, "test", TargetRef.ofSummon(playerId, summonId), 2);
        assertEquals(3, summon.hp());

        EffectOps effectOps = new EffectOps(new EffectContext(
                state,
                ctx,
                playerId,
                cardId,
                new TargetSelection(List.of(TargetRef.ofSummon(playerId, summonId))),
                out
        ));
        effectOps.heal(Target.ANY_ONE, 1);
        assertEquals(4, summon.hp());

        DamageOps.apply(state, ctx, out, "test", TargetRef.ofSummon(playerId, summonId), 99);

        assertNull(state.summon(summonId));
        assertFalse(player.activeSummons().contains(summonId));
        assertFalse(player.field().contains(cardId));
        assertTrue(player.grave().contains(cardId));
        assertEquals(Zone.GRAVE, sourceCard.zone());
    }

    @Test
    void summonStatusesApplyToOutgoingAndIncomingDamageHooks() {
        PlayerId playerId = new PlayerId("P1");
        EnemyId enemyId = new EnemyId("E1");
        CardDefId defId = new CardDefId("C_SUMMON");
        CardInstId cardId = new CardInstId("I_SUMMON");
        SummonInstId summonId = new SummonInstId(cardId.value());

        GameState state = new GameState(new SessionId(UUID.randomUUID()), 1L);
        PlayerState player = new PlayerState(playerId);
        EnemyState enemy = new EnemyState(enemyId, 30);
        state.players().put(playerId, player);
        state.enemies().put(enemyId, enemy);

        state.cardInstances().put(cardId, new CardInstance(cardId, defId, playerId, Zone.FIELD));
        SummonState summon = new SummonState(summonId, playerId, cardId, 10, 10, 0, 0, 0, false);
        state.summons().put(summonId, summon);

        String outId = "S_OUT";
        String inId = "S_IN";
        EngineContext ctx = new EngineContext(
                Map.of(defId, new CardDefinition(defId, "summon", CardType.SKILL, 0, Map.of(), Zone.GRAVE, false, "")),
                Map.of(),
                Map.of(
                        outId, new StatusDefinition(outId, "out", StatusKind.BUFF, StatusScope.CHARACTER, Set.of(), 1, true, ""),
                        inId, new StatusDefinition(inId, "in", StatusKind.BUFF, StatusScope.CHARACTER, Set.of(), 1, true, "")
                ),
                Map.of(
                        outId, new StatusEffect() {
                            @Override public String id() { return outId; }
                            @Override public int onOutgoingDamage(com.example.dueltower.engine.core.effect.status.StatusRuntime rt, StatusOwnerRef owner, TargetRef source, TargetRef target, int amount) { return amount * 2; }
                        },
                        inId, new StatusEffect() {
                            @Override public String id() { return inId; }
                            @Override public int onIncomingDamage(com.example.dueltower.engine.core.effect.status.StatusRuntime rt, StatusOwnerRef owner, TargetRef source, TargetRef target, int amount) { return amount - 1; }
                        }
                )
        );

        summon.statusAdd(outId, 1);
        summon.statusAdd(inId, 1);

        DamageOps.apply(state, ctx, new ArrayList<>(), TargetRef.ofSummon(playerId, summonId), "summon", TargetRef.ofEnemy(enemyId), 2);
        assertEquals(26, enemy.hp());

        List<GameEvent> out = new ArrayList<>();
        DamageOps.apply(state, ctx, out, TargetRef.ofEnemy(enemyId), "enemy", TargetRef.ofSummon(playerId, summonId), 3);
        assertEquals(8, summon.hp());
    }

    @Test
    void victoryCheckIgnoresSummonsAsPrincipals() {
        GameState state = new GameState(new SessionId(UUID.randomUUID()), 1L);

        PlayerId playerId = new PlayerId("P1");
        PlayerState player = new PlayerState(playerId);
        player.hp(0);
        state.players().put(playerId, player);

        // 소환체가 살아 있어도 플레이어 본체가 전멸이면 패배
        CardInstId sourceCardId = new CardInstId("I_SUMMON");
        SummonInstId summonId = new SummonInstId(sourceCardId.value());
        state.summons().put(summonId, new SummonState(summonId, playerId, sourceCardId, 5, 5, 0, 0, 0, false));

        EnemyState enemy = new EnemyState(new EnemyId("E1"), 1);
        state.enemies().put(enemy.enemyId(), enemy);

        assertEquals(VictoryOps.Outcome.PLAYERS_LOSE, VictoryOps.check(state));
    }
}
