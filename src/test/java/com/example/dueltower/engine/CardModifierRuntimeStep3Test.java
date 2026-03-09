package com.example.dueltower.engine;

import com.example.dueltower.content.card.model.OwnedCardModifier;
import com.example.dueltower.content.cardmodifier.cmdb.*;
import com.example.dueltower.engine.command.PlayCardCommand;
import com.example.dueltower.engine.command.StartCombatCommand;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.core.combat.DamageOps;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.*;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CardModifierRuntimeStep3Test {

    @Test
    void weakenedCostPlusOneIncreasesPlayCost() {
        Fx fx = new Fx();
        CardInstId id = fx.addHandCardWithModifiers(
                "NORMAL_STRIKE",
                List.of(new OwnedCardModifier(CardModifierIds.WEAKENED_COST_PLUS_ONE, 1))
        );
        fx.startMainTurn();
        fx.player.ap(2);

        EngineResult res = fx.process(new PlayCardCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                id,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))
        ));

        assertTrue(res.accepted());
        assertEquals(0, fx.player.ap());
    }

    @Test
    void weakenedSelfDamageAppliesBeforeResolve() {
        Fx fx = new Fx();
        CardInstId id = fx.addHandCardWithModifiers("NORMAL_STRIKE", List.of(new OwnedCardModifier(CardModifierIds.WEAKENED_SELF_DAMAGE_10, 1)));
        fx.startMainTurn();
        int playerBefore = fx.player.hp();
        int enemyBefore = fx.enemy.hp();

        EngineResult res = fx.process(new PlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, id,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));

        assertTrue(res.accepted());
        assertEquals(playerBefore - 10, fx.player.hp());
        assertEquals(enemyBefore - 5, fx.enemy.hp());
    }

    @Test
    void weakenedFinalHalfHalvesOutgoingDamage() {
        Fx fx = new Fx();
        CardInstId id = fx.addHandCardWithModifiers("NORMAL_STRIKE", List.of(new OwnedCardModifier(CardModifierIds.WEAKENED_FINAL_HALF, 1)));
        fx.startMainTurn();
        int enemyBefore = fx.enemy.hp();

        EngineResult res = fx.process(new PlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, id,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));
        assertTrue(res.accepted());
        assertEquals(enemyBefore - 2, fx.enemy.hp());
    }

    @Test
    void weakenedFinalHalfHalvesOutgoingHeal() {
        Fx fx = new Fx();
        fx.player.hp(10);
        CardInstId id = fx.addHandCardWithModifiers("SELF_HEAL", List.of(new OwnedCardModifier(CardModifierIds.WEAKENED_FINAL_HALF, 1)));
        fx.startMainTurn();

        EngineResult res = fx.process(new PlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, id, TargetSelection.empty()));
        assertTrue(res.accepted());
        assertEquals(12, fx.player.hp());
    }

    @Test
    void weakenedRandomEnemyOneReroutesDeterministically() {
        Fx fx1 = new Fx();
        Fx fx2 = new Fx();
        fx1.addEnemy("E2");
        fx2.addEnemy("E2");

        CardInstId id1 = fx1.addHandCardWithModifiers("NORMAL_STRIKE", List.of(new OwnedCardModifier(CardModifierIds.WEAKENED_RANDOM_ENEMY_ONE, 1)));
        CardInstId id2 = fx2.addHandCardWithModifiers("NORMAL_STRIKE", List.of(new OwnedCardModifier(CardModifierIds.WEAKENED_RANDOM_ENEMY_ONE, 1)));
        fx1.startMainTurn();
        fx2.startMainTurn();

        fx1.process(new PlayCardCommand(UUID.randomUUID(), fx1.state.version(), fx1.playerId, id1,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx1.enemyId)))));
        fx2.process(new PlayCardCommand(UUID.randomUUID(), fx2.state.version(), fx2.playerId, id2,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx2.enemyId)))));

        assertEquals(fx1.enemy.hp(), fx2.enemy.hp());
        assertEquals(fx1.state.enemy(new EnemyId("E2")).hp(), fx2.state.enemy(new EnemyId("E2")).hp());
    }

    @Test
    void weakenedDiscardFailsWhenNoOtherSkill() {
        Fx fx = new Fx();
        CardInstId id = fx.addHandCardWithModifiers("NORMAL_STRIKE", List.of(new OwnedCardModifier(CardModifierIds.WEAKENED_DISCARD_ONE_SKILL, 1)));
        fx.startMainTurn();

        EngineResult res = fx.process(new PlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, id,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));
        assertFalse(res.accepted());
    }

    @Test
    void weakenedDiscardDiscardsExactlyOneOtherSkill() {
        Fx fx = new Fx();
        CardInstId id = fx.addHandCardWithModifiers("NORMAL_STRIKE", List.of(new OwnedCardModifier(CardModifierIds.WEAKENED_DISCARD_ONE_SKILL, 1)));
        CardInstId other = fx.addHandCard("FILLER");
        fx.startMainTurn();

        EngineResult res = fx.process(new PlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, id,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));
        assertTrue(res.accepted());
        assertFalse(fx.player.hand().contains(other));
        assertTrue(fx.player.grave().contains(other));
    }

    @Test
    void strengthenedMarkerIsNoOp() {
        Fx fx = new Fx();
        CardInstId id = fx.addHandCardWithModifiers("NORMAL_STRIKE", List.of(new OwnedCardModifier(CardModifierIds.STRENGTHENED, 1)));
        fx.startMainTurn();
        int before = fx.enemy.hp();

        EngineResult res = fx.process(new PlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, id,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));
        assertTrue(res.accepted());
        assertEquals(before - 5, fx.enemy.hp());
    }

    @Test
    void unknownModifierIsNoOp() {
        Fx fx = new Fx();
        CardInstId id = fx.addHandCardWithModifiers("NORMAL_STRIKE", List.of(new OwnedCardModifier("UNKNOWN_MOD", 3)));
        fx.startMainTurn();

        EngineResult res = fx.process(new PlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, id,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));
        assertTrue(res.accepted());
    }

    private static final class Fx {
        final GameState state = new GameState(new SessionId(UUID.randomUUID()), 7L);
        final GameEngine engine = new GameEngine();
        final PlayerId playerId = new PlayerId("P1");
        EnemyId enemyId = new EnemyId("E1");
        final PlayerState player = new PlayerState(playerId);
        final EnemyState enemy = new EnemyState(enemyId, 40);
        final EngineContext ctx;

        Fx() {
            player.hp(player.maxHp());
            player.ap(player.maxAp());
            state.players().put(playerId, player);
            state.enemies().put(enemyId, enemy);

            Map<CardDefId, CardDefinition> defs = new HashMap<>();
            Map<CardDefId, CardEffect> effects = new HashMap<>();
            register(defs, effects, new TestCard("FILLER", CardType.SKILL, 1, 0, 0));
            register(defs, effects, new TestCard("NORMAL_STRIKE", CardType.SKILL, 1, 5, 0));
            register(defs, effects, new TestCard("SELF_HEAL", CardType.SKILL, 1, 0, 4));

            var cm = List.of(
                    new CM001_StrengthenedMarker(), new CM002_WeakenedMarker(),
                    new CM101_WeakenedCostPlusOne(), new CM102_WeakenedSelfDamage10(),
                    new CM103_WeakenedFinalHalf(), new CM104_WeakenedRandomEnemyOne(),
                    new CM105_WeakenedDiscardOneSkill()
            );
            Map<String, CardModifierDefinition> modDefs = new HashMap<>();
            Map<String, com.example.dueltower.engine.core.effect.cardmodifier.CardModifierEffect> modEffects = new HashMap<>();
            for (var bp : cm) {
                modDefs.put(bp.id(), bp.definition());
                modEffects.put(bp.id(), bp);
            }
            ctx = new EngineContext(defs, effects, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), modDefs, modEffects);
        }

        void register(Map<CardDefId, CardDefinition> defs, Map<CardDefId, CardEffect> effects, TestCard tc) {
            defs.put(new CardDefId(tc.id), tc.definition());
            effects.put(new CardDefId(tc.id), tc);
        }

        CardInstId addHandCard(String defId) { return addHandCardWithModifiers(defId, List.of()); }

        CardInstId addHandCardWithModifiers(String defId, List<OwnedCardModifier> modifiers) {
            CardInstId id = Ids.newCardInstId();
            CardInstance ci = new CardInstance(id, new CardDefId(defId), playerId, Zone.HAND, "owned", modifiers);
            state.cardInstances().put(id, ci);
            player.hand().add(id);
            return id;
        }

        void addEnemy(String id) {
            state.enemies().put(new EnemyId(id), new EnemyState(new EnemyId(id), 40));
        }

        void startMainTurn() {
            EngineResult r = process(new StartCombatCommand(UUID.randomUUID(), state.version(), playerId));
            assertTrue(r.accepted());
            state.combat().phase(CombatPhase.MAIN);
            state.combat().turnOrder().clear();
            state.combat().turnOrder().add(TargetRef.ofPlayer(playerId));
            state.combat().currentTurnIndex(0);
            player.ap(3);
        }

        EngineResult process(PlayCardCommand c) { return engine.process(state, ctx, c); }
        EngineResult process(StartCombatCommand c) { return engine.process(state, ctx, c); }
    }

    private static final class TestCard implements CardEffect {
        final String id;
        final CardType type;
        final int cost;
        final int damage;
        final int heal;

        TestCard(String id, CardType type, int cost, int damage, int heal) {
            this.id = id; this.type = type; this.cost = cost; this.damage = damage; this.heal = heal;
        }

        @Override public String id() { return id; }

        CardDefinition definition() {
            return new CardDefinition(new CardDefId(id), id, type, cost, Map.of(), Zone.GRAVE, false, id);
        }

        @Override public List<String> validate(EffectContext ec) { return List.of(); }

        @Override
        public void resolve(EffectContext ec) {
            if (damage > 0) {
                DamageOps.apply(ec.state(), ec.ctx(), ec.out(), TargetRef.ofPlayer(ec.actor()), ec.cardId(), id,
                        ec.selection().requireOne(), damage, com.example.dueltower.engine.core.combat.DamageFlags.NONE);
            }
            if (heal > 0) {
                com.example.dueltower.engine.core.combat.HealOps.apply(ec.state(), ec.ctx(), ec.out(), TargetRef.ofPlayer(ec.actor()), ec.cardId(), id,
                        TargetRef.ofPlayer(ec.actor()), heal);
            }
        }
    }
}
