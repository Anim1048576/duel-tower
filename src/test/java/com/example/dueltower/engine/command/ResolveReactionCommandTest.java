package com.example.dueltower.engine.command;

import com.example.dueltower.content.card.cdb.C005_EmergencyAttack;
import com.example.dueltower.content.status.sdb.S001_Shield;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.core.combat.DamageOps;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardInstance;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.CombatPhase;
import com.example.dueltower.engine.model.CombatState;
import com.example.dueltower.engine.model.EnemyState;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.EnemyId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import com.example.dueltower.engine.model.PendingDecision;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.ReactionTrigger;
import com.example.dueltower.engine.model.StatusDefinition;
import com.example.dueltower.engine.model.TargetRef;
import com.example.dueltower.engine.model.TargetSelection;
import com.example.dueltower.engine.model.Zone;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ResolveReactionCommandTest {
    @Test
    void enemyAttackDamageOpensReactionPending() {
        Fixture fx = Fixture.basic();
        CardInstId emergency = fx.addPlayerHandCard("C005");
        CardInstId enemyAttack = fx.addEnemyHandCard("ENEMY_ATTACK");
        fx.startEnemyTurn();

        EngineResult result = fx.process(new EnemyPlayCardCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.enemyId,
                enemyAttack,
                new TargetSelection(List.of(TargetRef.ofPlayer(fx.playerId)))
        ));

        assertTrue(result.accepted());
        PendingDecision.ReactionCard pending = assertInstanceOf(PendingDecision.ReactionCard.class, fx.player.pendingDecision());
        assertEquals(ReactionTrigger.AFTER_ENEMY_ATTACK_DAMAGED_SELF.name(), pending.reason());
        assertTrue(pending.candidateIds().contains(emergency));
        assertTrue(pending.skippable());
    }

    @Test
    void zeroActualDamageDoesNotOpenReactionPending() {
        Fixture fx = Fixture.basic();
        fx.addPlayerHandCard("C005");
        CardInstId enemyAttack = fx.addEnemyHandCard("ENEMY_ATTACK");
        fx.player.statusSet(S001_Shield.ID, 99);
        fx.startEnemyTurn();

        EngineResult result = fx.process(new EnemyPlayCardCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.enemyId,
                enemyAttack,
                new TargetSelection(List.of(TargetRef.ofPlayer(fx.playerId)))
        ));

        assertTrue(result.accepted());
        assertNull(fx.player.pendingDecision());
    }

    @Test
    void skipClearsReactionPendingWithoutMovingCardOrDamagingEnemy() {
        Fixture fx = Fixture.withReactionPending();
        CardInstId emergency = fx.emergencyCard;
        int enemyHpBefore = fx.enemy.hp();

        EngineResult result = fx.process(new ResolveReactionCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                null
        ));

        assertTrue(result.accepted());
        assertNull(fx.player.pendingDecision());
        assertTrue(fx.player.hand().contains(emergency));
        assertFalse(fx.player.grave().contains(emergency));
        assertEquals(enemyHpBefore, fx.enemy.hp());
    }

    @Test
    void emergencyAttackReactionDealsHalfAttackWithoutApOrCardsPlayedCost() {
        Fixture fx = Fixture.withReactionPending();
        CardInstId emergency = fx.emergencyCard;
        int enemyHpBefore = fx.enemy.hp();
        int apBefore = fx.player.ap();
        int cardsPlayedBefore = fx.player.cardsPlayedThisTurn();

        EngineResult result = fx.process(new ResolveReactionCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                emergency
        ));

        assertTrue(result.accepted());
        assertEquals(enemyHpBefore - 2, fx.enemy.hp());
        assertEquals(apBefore, fx.player.ap());
        assertEquals(cardsPlayedBefore, fx.player.cardsPlayedThisTurn());
        assertFalse(fx.player.hand().contains(emergency));
        assertTrue(fx.player.grave().contains(emergency));
        assertNull(fx.player.pendingDecision());
    }

    @Test
    void normalUseAndReactionUseHaveDifferentCostAndDamageRules() {
        Fixture normal = Fixture.basic();
        CardInstId normalEmergency = normal.addPlayerHandCard("C005");
        normal.startPlayerTurn();
        int normalEnemyHpBefore = normal.enemy.hp();
        int normalApBefore = normal.player.ap();

        EngineResult normalUse = normal.process(new PlayCardCommand(
                UUID.randomUUID(),
                normal.state.version(),
                normal.playerId,
                normalEmergency,
                new TargetSelection(List.of(TargetRef.ofEnemy(normal.enemyId)))
        ));

        assertTrue(normalUse.accepted());
        assertEquals(normalEnemyHpBefore - 5, normal.enemy.hp());
        assertEquals(normalApBefore - 1, normal.player.ap());
        assertEquals(1, normal.player.cardsPlayedThisTurn());

        Fixture reaction = Fixture.withReactionPending();
        int reactionEnemyHpBefore = reaction.enemy.hp();
        int reactionApBefore = reaction.player.ap();

        EngineResult reactionUse = reaction.process(new ResolveReactionCommand(
                UUID.randomUUID(),
                reaction.state.version(),
                reaction.playerId,
                reaction.emergencyCard
        ));

        assertTrue(reactionUse.accepted());
        assertEquals(reactionEnemyHpBefore - 2, reaction.enemy.hp());
        assertEquals(reactionApBefore, reaction.player.ap());
        assertEquals(0, reaction.player.cardsPlayedThisTurn());
    }

    @Test
    void nonCandidateCardIsRejectedWithoutStateChange() {
        Fixture fx = Fixture.withReactionPending();
        CardInstId nonCandidate = fx.addPlayerHandCard("FILLER");
        PendingDecision pendingBefore = fx.player.pendingDecision();
        int enemyHpBefore = fx.enemy.hp();
        long versionBefore = fx.state.version();

        EngineResult result = fx.process(new ResolveReactionCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                nonCandidate
        ));

        assertFalse(result.accepted());
        assertSame(pendingBefore, fx.player.pendingDecision());
        assertEquals(enemyHpBefore, fx.enemy.hp());
        assertTrue(fx.player.hand().contains(nonCandidate));
        assertEquals(versionBefore, fx.state.version());
    }

    @Test
    void reactionDamageDoesNotOpenAnotherReactionPending() {
        Fixture fx = Fixture.withReactionPending();

        EngineResult result = fx.process(new ResolveReactionCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                fx.emergencyCard
        ));

        assertTrue(result.accepted());
        assertNull(fx.player.pendingDecision());
    }

    @Test
    void reactionUseIsRejectedWhenSourceEnemyIsNoLongerValid() {
        Fixture fx = Fixture.withReactionPending();
        PendingDecision pendingBefore = fx.player.pendingDecision();
        fx.enemy.hp(0);

        EngineResult result = fx.process(new ResolveReactionCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                fx.emergencyCard
        ));

        assertFalse(result.accepted());
        assertSame(pendingBefore, fx.player.pendingDecision());
        assertTrue(fx.player.hand().contains(fx.emergencyCard));
    }

    private static final class Fixture {
        final GameState state = new GameState(new SessionId(UUID.randomUUID()), 123L);
        final GameEngine engine = new GameEngine();
        final PlayerId playerId = new PlayerId("P1");
        final EnemyId enemyId = new EnemyId("E1");
        final PlayerState player = new PlayerState(playerId);
        final EnemyState enemy = new EnemyState(enemyId, 40);
        final EngineContext ctx;
        CardInstId emergencyCard;

        private Fixture() {
            player.body(2);
            player.skill(2);
            player.sense(2);
            player.will(0);
            player.hp(player.maxHp());
            player.ap(player.maxAp());
            enemy.attackPower(5);
            state.players().put(playerId, player);
            state.enemies().put(enemyId, enemy);

            Map<CardDefId, CardDefinition> defs = new HashMap<>();
            Map<CardDefId, CardEffect> effects = new HashMap<>();
            register(defs, effects, new C005_EmergencyAttack());
            register(defs, effects, new FixedDamageCard("ENEMY_ATTACK", 0, 5));
            register(defs, effects, new FixedDamageCard("FILLER", 0, 0));

            S001_Shield shield = new S001_Shield();
            Map<String, StatusDefinition> statusDefs = Map.of(shield.id(), shield.definition());
            Map<String, com.example.dueltower.engine.core.effect.status.StatusEffect> statusEffects = Map.of(shield.id(), shield);

            ctx = new EngineContext(defs, effects, statusDefs, statusEffects);
        }

        static Fixture basic() {
            return new Fixture();
        }

        static Fixture withReactionPending() {
            Fixture fx = basic();
            fx.emergencyCard = fx.addPlayerHandCard("C005");
            CardInstId enemyAttack = fx.addEnemyHandCard("ENEMY_ATTACK");
            fx.startEnemyTurn();
            EngineResult attack = fx.process(new EnemyPlayCardCommand(
                    UUID.randomUUID(),
                    fx.state.version(),
                    fx.enemyId,
                    enemyAttack,
                    new TargetSelection(List.of(TargetRef.ofPlayer(fx.playerId)))
            ));
            assertTrue(attack.accepted());
            assertTrue(fx.player.pendingDecision() instanceof PendingDecision.ReactionCard);
            return fx;
        }

        EngineResult process(GameCommand command) {
            return engine.process(state, ctx, command);
        }

        void startEnemyTurn() {
            startTurn(TargetRef.ofEnemy(enemyId));
        }

        void startPlayerTurn() {
            startTurn(TargetRef.ofPlayer(playerId));
        }

        private void startTurn(TargetRef actor) {
            CombatState combat = new CombatState();
            combat.turnOrder().add(actor);
            combat.currentTurnIndex(0);
            combat.phase(CombatPhase.MAIN);
            state.combat(combat);
        }

        CardInstId addPlayerHandCard(String defId) {
            CardInstId id = Ids.newCardInstId();
            state.cardInstances().put(id, new CardInstance(id, new CardDefId(defId), playerId, Zone.HAND));
            player.hand().add(id);
            return id;
        }

        CardInstId addEnemyHandCard(String defId) {
            CardInstId id = Ids.newCardInstId();
            state.cardInstances().put(id, new CardInstance(id, new CardDefId(defId), new PlayerId(enemyId.value()), Zone.HAND));
            return id;
        }

        private static void register(Map<CardDefId, CardDefinition> defs, Map<CardDefId, CardEffect> effects, C005_EmergencyAttack effect) {
            CardDefinition def = effect.definition();
            defs.put(def.id(), def);
            effects.put(def.id(), effect);
        }

        private static void register(Map<CardDefId, CardDefinition> defs, Map<CardDefId, CardEffect> effects, FixedDamageCard effect) {
            CardDefinition def = effect.definition();
            defs.put(def.id(), def);
            effects.put(def.id(), effect);
        }
    }

    private static final class FixedDamageCard implements CardEffect {
        private final String id;
        private final int cost;
        private final int damage;

        private FixedDamageCard(String id, int cost, int damage) {
            this.id = id;
            this.cost = cost;
            this.damage = damage;
        }

        @Override
        public String id() {
            return id;
        }

        CardDefinition definition() {
            return new CardDefinition(new CardDefId(id), id, CardType.SKILL, cost, Map.of(), Zone.GRAVE, false, id);
        }

        @Override
        public List<String> validate(EffectContext ec) {
            if (damage > 0 && (ec.selection() == null || ec.selection().targets().size() != 1)) {
                return List.of("exactly one target required");
            }
            return List.of();
        }

        @Override
        public void resolve(EffectContext ec) {
            if (damage <= 0) return;
            TargetRef target = ec.selection().requireOne();
            TargetRef source = ec.actorRef();
            DamageOps.apply(
                    ec.state(),
                    ec.ctx(),
                    ec.out(),
                    source,
                    ec.cardId(),
                    id,
                    target,
                    damage,
                    KeywordOps.damageFlags(ec.state(), ec.ctx(), source, ec.cardId(), target)
            );
        }
    }
}
