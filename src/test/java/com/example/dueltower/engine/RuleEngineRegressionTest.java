package com.example.dueltower.engine;

import com.example.dueltower.content.keyword.kdb.K008_Accurate;
import com.example.dueltower.content.keyword.kdb.K009_Penetration;
import com.example.dueltower.content.keyword.kdb.K010_Tenacity;
import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.content.status.sdb.*;
import com.example.dueltower.engine.command.*;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.core.combat.CombatStatuses;
import com.example.dueltower.engine.core.combat.DamageOps;
import com.example.dueltower.engine.core.combat.TurnPhases;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class RuleEngineRegressionTest {

    @Test
    void combatStartDrawsOpeningHandFromDeckTopOrder() {
        TestFixture fx = TestFixture.basic();
        List<CardInstId> orderedDeck = fx.addDeckCards(fx.player, "FILLER", 6);

        EngineResult result = fx.process(new StartCombatCommand(UUID.randomUUID(), fx.state.version(), fx.playerId));

        assertTrue(result.accepted());
        assertTrue(result.events().stream().anyMatch(e -> e instanceof GameEvent.LogAppended l && l.line().contains("draws 4 (combat start)")));
        assertEquals(5, fx.player.hand().size(), "opening 4 + first turn draw 1");
        assertEquals(orderedDeck.subList(0, 4), fx.player.hand().subList(0, 4), "opening hand should draw deck top order");
    }

    @Test
    void turnStartDrawFlowDrawsTwoBelowFourElseOne() {
        TestFixture fx = TestFixture.basic();
        fx.state.combat(new CombatState());

        fx.player.hand().clear();
        fx.addHandCards(fx.player, "FILLER", 3);
        fx.addDeckCards(fx.player, "FILLER", 5);

        TurnPhases.turnStart(fx.state, fx.ctx, TargetRef.ofPlayer(fx.playerId), new ArrayList<>(), "TEST");
        assertEquals(5, fx.player.hand().size(), "3 cards in hand should draw 2");

        fx.player.hand().clear();
        fx.addHandCards(fx.player, "FILLER", 4);
        TurnPhases.turnStart(fx.state, fx.ctx, TargetRef.ofPlayer(fx.playerId), new ArrayList<>(), "TEST");
        assertEquals(5, fx.player.hand().size(), "4 cards in hand should draw 1");
    }

    @Test
    void handLimitOverflowCreatesDiscardPendingDecision() {
        TestFixture fx = TestFixture.basic();
        fx.state.combat(new CombatState());

        fx.player.hand().clear();
        fx.addHandCards(fx.player, "FILLER", 6);
        fx.addDeckCards(fx.player, "FILLER", 2);

        TurnPhases.turnStart(fx.state, fx.ctx, TargetRef.ofPlayer(fx.playerId), new ArrayList<>(), "TEST");

        assertInstanceOf(PendingDecision.DiscardToHandLimit.class, fx.player.pendingDecision());
        PendingDecision.DiscardToHandLimit pd = (PendingDecision.DiscardToHandLimit) fx.player.pendingDecision();
        assertEquals(6, pd.limit());
    }

    @Test
    void tenacityApDebtIsRecordedAndAppliedAtTurnEndRefill() {
        TestFixture fx = TestFixture.basic();
        fx.addDeckCards(fx.player, "FILLER", 10);
        CardInstId tenacityCard = fx.addHandCard(fx.player, "TENACITY_STRIKE");

        fx.player.ap(1);
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        EngineResult play = fx.process(new PlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, tenacityCard,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));

        assertTrue(play.accepted());
        assertEquals(0, fx.player.ap());
        assertTrue(fx.player.usedTenacityThisTurn());
        assertEquals(2, fx.player.tenacityDebtThisTurn());

        EngineResult end = fx.process(new EndTurnCommand(UUID.randomUUID(), fx.state.version(), fx.playerId));
        assertTrue(end.accepted());
        assertEquals(1, fx.player.ap(), "maxAp(3) - debt(2)");
    }

    @Test
    void sureHitIgnoresEvasion() {
        TestFixture fx = TestFixture.basic();
        CardInstId accurate = fx.addHandCard(fx.player, "ACCURATE_STRIKE");

        fx.enemy.statusSet(S004_Evasion.ID, 2);
        int hpBefore = fx.enemy.hp();

        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        EngineResult play = fx.process(new PlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, accurate,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));

        assertTrue(play.accepted());
        assertEquals(hpBefore - 5, fx.enemy.hp());
        assertEquals(2, fx.enemy.status(S004_Evasion.ID), "evasion should not be consumed by sure-hit");
    }

    @Test
    void pierceIgnoresShieldAndBarrierMitigation() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        fx.enemy.statusSet(S001_Shield.ID, 3);
        fx.state.combat().factionStatusValues(CombatState.FactionId.ENEMIES).put(S301_Barrier.ID, 4);

        List<GameEvent> normalEvents = new ArrayList<>();
        DamageOps.apply(fx.state, fx.ctx, normalEvents, TargetRef.ofPlayer(fx.playerId), "normal", TargetRef.ofEnemy(fx.enemyId), 5,
                KeywordOps.damageFlags(fx.state, fx.ctx, TargetRef.ofPlayer(fx.playerId), fx.addHandCard(fx.player, "NORMAL_STRIKE"), TargetRef.ofEnemy(fx.enemyId)));

        assertEquals(0, fx.enemy.maxHp() - fx.enemy.hp(), "without pierce, mitigation should absorb all");
        assertEquals(2, fx.enemy.status(S001_Shield.ID), "barrier(4) then shield(1)");

        fx.enemy.hp(fx.enemy.maxHp());
        fx.enemy.statusSet(S001_Shield.ID, 3);
        fx.state.combat().factionStatusValues(CombatState.FactionId.ENEMIES).put(S301_Barrier.ID, 4);

        DamageOps.apply(fx.state, fx.ctx, new ArrayList<>(), TargetRef.ofPlayer(fx.playerId), "pierce", TargetRef.ofEnemy(fx.enemyId), 5,
                KeywordOps.damageFlags(fx.state, fx.ctx, TargetRef.ofPlayer(fx.playerId), fx.addHandCard(fx.player, "PIERCE_STRIKE"), TargetRef.ofEnemy(fx.enemyId)));

        assertEquals(fx.enemy.maxHp() - 5, fx.enemy.hp());
        assertEquals(3, fx.enemy.status(S001_Shield.ID));
        assertEquals(4, fx.state.combat().factionStatusValues(CombatState.FactionId.ENEMIES).get(S301_Barrier.ID));
    }

    @Test
    void turnEndProcessesRegenAndPainWithStackDecay() {
        TestFixture fx = TestFixture.basic();
        fx.state.combat(new CombatState());
        fx.player.hp(10);
        fx.player.statusSet(S002_Regeneration.ID, 6);
        fx.player.statusSet(S101_Pain.ID, 4);

        TurnPhases.turnEnd(fx.state, fx.ctx, TargetRef.ofPlayer(fx.playerId), new ArrayList<>(), "TEST");

        assertEquals(12, fx.player.hp());
        assertEquals(3, fx.player.status(S002_Regeneration.ID));
        assertEquals(2, fx.player.status(S101_Pain.ID));
    }

    @Test
    void statusRestrictionsAndDamageModifiersRemainStable() {
        TestFixture fx = TestFixture.basic();
        CardInstId skill = fx.addHandCard(fx.player, "NORMAL_STRIKE");
        fx.player.exCard(fx.addExCard(fx.player, "EX_BLAST"));
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        fx.player.statusSet(S102_Stun.ID, 1);
        EngineResult stunnedPlay = fx.process(new PlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, skill,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));
        assertFalse(stunnedPlay.accepted());
        assertTrue(stunnedPlay.errors().stream().anyMatch(s -> s.contains("stun: cannot play skill cards")));

        EngineResult stunnedEx = fx.process(new UseExCommand(UUID.randomUUID(), fx.state.version(), fx.playerId,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));
        assertFalse(stunnedEx.accepted());
        assertTrue(stunnedEx.errors().stream().anyMatch(s -> s.contains("stun: cannot use EX")));

        fx.player.statusSet(S102_Stun.ID, 0);
        fx.player.statusSet(S108_Seal.ID, 1);
        EngineResult sealedEx = fx.process(new UseExCommand(UUID.randomUUID(), fx.state.version(), fx.playerId,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));
        assertFalse(sealedEx.accepted());
        assertTrue(sealedEx.errors().stream().anyMatch(s -> s.contains("seal: cannot use EX")));

        fx.player.statusSet(S108_Seal.ID, 0);
        fx.player.statusSet(S103_Pressure.ID, 3);
        fx.player.ap(1);
        EngineResult pressurePlay = fx.process(new PlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, skill,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));
        assertFalse(pressurePlay.accepted());
        assertTrue(pressurePlay.errors().stream().anyMatch(s -> s.contains("not enough ap")));

        fx.player.statusSet(S103_Pressure.ID, 0);
        fx.player.statusSet(S104_Destruction.ID, 2);
        fx.player.statusSet(S105_Weak.ID, 1);
        fx.enemy.statusSet(S106_Vulnerable.ID, 1);
        fx.player.ap(10);
        int hpBefore = fx.player.hp();
        int enemyHpBefore = fx.enemy.hp();

        EngineResult play = fx.process(new PlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, skill,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));
        assertTrue(play.accepted());
        assertEquals(hpBefore - 2, fx.player.hp(), "destruction recoil");
        assertEquals(enemyHpBefore - 5, fx.enemy.hp(), "base 5 -> weak -1 -> vulnerable +1");
    }

    @Test
    void exCooldownExpiresAfterRoundBoundary() {
        TestFixture fx = TestFixture.basic();
        fx.player.exCard(fx.addExCard(fx.player, "EX_BLAST"));
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        EngineResult use = fx.process(new UseExCommand(UUID.randomUUID(), fx.state.version(), fx.playerId,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));
        assertTrue(use.accepted());
        assertEquals(2, fx.player.exCooldownUntilRound());

        EngineResult blocked = fx.process(new UseExCommand(UUID.randomUUID(), fx.state.version(), fx.playerId,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));
        assertFalse(blocked.accepted());
        assertTrue(blocked.errors().contains("ex on cooldown"));

        assertTrue(fx.process(new EndTurnCommand(UUID.randomUUID(), fx.state.version(), fx.playerId)).accepted()); // round 2
        assertTrue(fx.process(new EndTurnCommand(UUID.randomUUID(), fx.state.version(), fx.playerId)).accepted()); // round 3

        EngineResult available = fx.process(new UseExCommand(UUID.randomUUID(), fx.state.version(), fx.playerId,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));
        assertTrue(available.accepted());
    }


    @Test
    void enemyPlayCardFailsWhenNotEnemyTurn() {
        TestFixture fx = TestFixture.basic();
        CardInstId enemyCard = fx.addEnemyHandCard("NORMAL_STRIKE");
        fx.enemy.ap(3);
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        EngineResult res = fx.process(new EnemyPlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.enemyId, enemyCard,
                new TargetSelection(List.of(TargetRef.ofPlayer(fx.playerId)))));

        assertFalse(res.accepted());
        assertTrue(res.errors().contains("not enemy turn"));
    }

    @Test
    void enemyUseExFailsOnPlayerTurn() {
        TestFixture fx = TestFixture.basic();
        fx.enemy.exCard(fx.addEnemyExCard("EX_BLAST"));
        fx.enemy.statusSet(com.example.dueltower.engine.core.effect.keyword.EnemyExOps.BOSS_EX_READY, 1);
        fx.enemy.ap(5);
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        EngineResult res = fx.process(new EnemyUseExCommand(UUID.randomUUID(), fx.state.version(), fx.enemyId,
                new TargetSelection(List.of(TargetRef.ofPlayer(fx.playerId)))));

        assertFalse(res.accepted());
        assertTrue(res.errors().contains("not enemy turn"));
    }

    @Test
    void enemyExCooldownValidationApplies() {
        TestFixture fx = TestFixture.basic();
        fx.enemy.exCard(fx.addEnemyExCard("EX_BLAST"));
        fx.enemy.statusSet(com.example.dueltower.engine.core.effect.keyword.EnemyExOps.BOSS_EX_READY, 1);
        fx.enemy.ap(5);
        fx.startSimpleCombat();
        fx.forceMainTurnForEnemy();

        EngineResult use = fx.process(new EnemyUseExCommand(UUID.randomUUID(), fx.state.version(), fx.enemyId,
                new TargetSelection(List.of(TargetRef.ofPlayer(fx.playerId)))));
        assertTrue(use.accepted());

        fx.enemy.statusSet(com.example.dueltower.engine.core.effect.keyword.EnemyExOps.BOSS_EX_READY, 1);
        fx.enemy.ap(5);
        EngineResult blocked = fx.process(new EnemyUseExCommand(UUID.randomUUID(), fx.state.version(), fx.enemyId,
                new TargetSelection(List.of(TargetRef.ofPlayer(fx.playerId)))));

        assertFalse(blocked.accepted());
        assertTrue(blocked.errors().contains("ex on cooldown"));
    }

    @Test
    void enemyPlayCardSuccessPathDealsDamageToPlayer() {
        TestFixture fx = TestFixture.basic();
        CardInstId enemyCard = fx.addEnemyHandCard("NORMAL_STRIKE");
        fx.enemy.ap(3);
        fx.startSimpleCombat();
        fx.forceMainTurnForEnemy();

        int hpBefore = fx.player.hp();
        EngineResult res = fx.process(new EnemyPlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.enemyId, enemyCard,
                new TargetSelection(List.of(TargetRef.ofPlayer(fx.playerId)))));

        assertTrue(res.accepted());
        assertEquals(hpBefore - 5, fx.player.hp());
        assertEquals(Zone.GRAVE, fx.state.card(enemyCard).zone());
    }

    @Test
    void drawFailsWithEmptyDeckAndGraveMarksBattleIncapacitated() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();
        fx.player.hand().clear();
        fx.player.deck().clear();
        fx.player.grave().clear();

        EngineResult draw = fx.process(new DrawCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, 1));

        assertTrue(draw.accepted());
        assertEquals(1, fx.player.status(CombatStatuses.BATTLE_INCAPACITATED));
        assertTrue(draw.events().stream().anyMatch(e -> e instanceof GameEvent.LogAppended l && l.line().contains("cannot draw: deck+grave empty")));
    }

    private static final class TestFixture {
        final GameState state = new GameState(new SessionId(UUID.randomUUID()), 7L);
        final GameEngine engine = new GameEngine();
        final PlayerId playerId = new PlayerId("P1");
        final EnemyId enemyId = new EnemyId("E1");
        final PlayerState player = new PlayerState(playerId);
        final EnemyState enemy = new EnemyState(enemyId, 40);
        final EngineContext ctx;

        private TestFixture() {
            player.body(5);
            player.skill(5);
            player.sense(4);
            player.will(0);
            player.hp(player.maxHp());
            player.ap(player.maxAp());

            enemy.attackPower(4);
            state.players().put(playerId, player);
            state.enemies().put(enemyId, enemy);

            Map<CardDefId, CardDefinition> defs = new HashMap<>();
            Map<CardDefId, CardEffect> effects = new HashMap<>();
            registerCard(defs, effects, new TestCardEffect("FILLER", CardType.SKILL, 0, Map.of(), 0));
            registerCard(defs, effects, new TestCardEffect("NORMAL_STRIKE", CardType.SKILL, 1, Map.of(), 5));
            registerCard(defs, effects, new TestCardEffect("TENACITY_STRIKE", CardType.SKILL, 3, Map.of(K010_Tenacity.ID, 1), 5));
            registerCard(defs, effects, new TestCardEffect("ACCURATE_STRIKE", CardType.SKILL, 1, Map.of(K008_Accurate.ID, 1), 5));
            registerCard(defs, effects, new TestCardEffect("PIERCE_STRIKE", CardType.SKILL, 1, Map.of(K009_Penetration.ID, 1), 5));
            registerCard(defs, effects, new TestCardEffect("EX_BLAST", CardType.EX, 1, Map.of(), 4));

            Map<String, StatusDefinition> statusDefs = new HashMap<>();
            Map<String, com.example.dueltower.engine.core.effect.status.StatusEffect> statusEffects = new HashMap<>();
            for (StatusBlueprint bp : List.of(
                    new S001_Shield(), new S002_Regeneration(), new S004_Evasion(),
                    new S101_Pain(), new S102_Stun(), new S103_Pressure(), new S104_Destruction(),
                    new S105_Weak(), new S106_Vulnerable(), new S108_Seal(), new S301_Barrier()
            )) {
                statusDefs.put(bp.id(), bp.definition());
                statusEffects.put(bp.id(), bp);
            }

            Map<String, KeywordDefinition> keywordDefs = new HashMap<>();
            Map<String, com.example.dueltower.engine.core.effect.keyword.KeywordEffect> keywordEffects = new HashMap<>();
            for (var bp : List.of(new K008_Accurate(), new K009_Penetration(), new K010_Tenacity())) {
                keywordDefs.put(bp.id(), bp.definition());
                keywordEffects.put(bp.id(), bp);
            }

            this.ctx = new EngineContext(defs, effects, statusDefs, statusEffects, keywordDefs, keywordEffects);
        }

        static TestFixture basic() {
            return new TestFixture();
        }

        EngineResult process(GameCommand command) {
            return engine.process(state, ctx, command);
        }

        void startSimpleCombat() {
            EngineResult result = process(new StartCombatCommand(UUID.randomUUID(), state.version(), playerId));
            assertTrue(result.accepted());
        }

        void forceMainTurnForPlayer() {
            CombatState cs = state.combat();
            assertNotNull(cs);
            cs.turnOrder().clear();
            cs.turnOrder().add(TargetRef.ofPlayer(playerId));
            cs.currentTurnIndex(0);
            cs.phase(CombatPhase.MAIN);
        }

        void forceMainTurnForEnemy() {
            CombatState cs = state.combat();
            assertNotNull(cs);
            cs.turnOrder().clear();
            cs.turnOrder().add(TargetRef.ofEnemy(enemyId));
            cs.currentTurnIndex(0);
            cs.phase(CombatPhase.MAIN);
        }

        List<CardInstId> addDeckCards(PlayerState owner, String defId, int count) {
            List<CardInstId> out = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                out.add(addCard(owner, defId, Zone.DECK));
            }
            return out;
        }

        List<CardInstId> addHandCards(PlayerState owner, String defId, int count) {
            List<CardInstId> out = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                out.add(addCard(owner, defId, Zone.HAND));
            }
            return out;
        }

        CardInstId addHandCard(PlayerState owner, String defId) {
            return addCard(owner, defId, Zone.HAND);
        }

        CardInstId addExCard(PlayerState owner, String defId) {
            return addCard(owner, defId, Zone.EX);
        }

        CardInstId addEnemyHandCard(String defId) {
            return addEnemyCard(defId, Zone.HAND);
        }

        CardInstId addEnemyExCard(String defId) {
            return addEnemyCard(defId, Zone.EX);
        }

        private CardInstId addEnemyCard(String defId, Zone zone) {
            CardInstId id = Ids.newCardInstId();
            CardInstance ci = new CardInstance(id, new CardDefId(defId), new PlayerId(enemyId.value()), zone);
            state.cardInstances().put(id, ci);
            if (zone == Zone.EX) {
                enemy.exCard(id);
            }
            return id;
        }

        private CardInstId addCard(PlayerState owner, String defId, Zone zone) {
            CardInstId id = Ids.newCardInstId();
            CardInstance ci = new CardInstance(id, new CardDefId(defId), owner.playerId(), zone);
            state.cardInstances().put(id, ci);
            switch (zone) {
                case HAND -> owner.hand().add(id);
                case DECK -> owner.deck().addLast(id);
                case EX -> owner.exCard(id);
                case GRAVE -> owner.grave().add(id);
                case FIELD -> owner.field().add(id);
                case EXCLUDED -> owner.excluded().add(id);
            }
            return id;
        }

        private static void registerCard(Map<CardDefId, CardDefinition> defs, Map<CardDefId, CardEffect> effects, TestCardEffect effect) {
            CardDefinition def = effect.definition();
            defs.put(def.id(), def);
            effects.put(def.id(), effect);
        }
    }

    private static final class TestCardEffect implements CardEffect {
        private final String id;
        private final CardType type;
        private final int cost;
        private final Map<String, Integer> keywords;
        private final int damage;

        private TestCardEffect(String id, CardType type, int cost, Map<String, Integer> keywords, int damage) {
            this.id = id;
            this.type = type;
            this.cost = cost;
            this.keywords = keywords;
            this.damage = damage;
        }

        @Override
        public String id() {
            return id;
        }

        CardDefinition definition() {
            return new CardDefinition(new CardDefId(id), id, type, cost, keywords, type == CardType.EX ? Zone.EX : Zone.GRAVE, false, id);
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
            TargetRef src = (ec.actor() != null && ec.state().enemy(new EnemyId(ec.actor().value())) != null)
                    ? TargetRef.ofEnemy(new EnemyId(ec.actor().value()))
                    : TargetRef.ofPlayer(ec.actor());
            DamageOps.apply(ec.state(), ec.ctx(), ec.out(), src, id, target, damage,
                    KeywordOps.damageFlags(ec.state(), ec.ctx(), src, ec.cardId(), target));
        }
    }
}
