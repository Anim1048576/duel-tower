package com.example.dueltower.engine;

import com.example.dueltower.common.util.Rational;
import com.example.dueltower.content.keyword.kdb.K007_ClearMind;
import com.example.dueltower.content.keyword.kdb.K008_Accurate;
import com.example.dueltower.content.keyword.kdb.K009_Penetration;
import com.example.dueltower.content.keyword.kdb.K010_Tenacity;
import com.example.dueltower.content.keyword.kdb.K011_Critical;
import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.content.status.sdb.*;
import com.example.dueltower.engine.command.*;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.core.combat.CombatStatuses;
import com.example.dueltower.engine.core.combat.CombatCleanupOps;
import com.example.dueltower.engine.core.combat.DamageOps;
import com.example.dueltower.engine.core.combat.TurnPhases;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.keyword.DamageKeywordCtx;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.core.effect.keyword.KeywordRuntime;
import com.example.dueltower.engine.core.effect.passive.PassiveOps;
import com.example.dueltower.engine.core.effect.status.StatusRuntime;
import com.example.dueltower.engine.event.GameEvent;
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
    void turnStartResetsOnlyConsumablesUsedThisTurn() {
        TestFixture fx = TestFixture.basic();
        fx.state.combat(new CombatState());
        fx.player.consumablesUsedThisTurn(1);
        fx.player.consumablesUsedThisCombat(2);
        fx.addDeckCards(fx.player, "FILLER", 2);

        TurnPhases.turnStart(fx.state, fx.ctx, TargetRef.ofPlayer(fx.playerId), new ArrayList<>(), "TEST");

        assertEquals(0, fx.player.consumablesUsedThisTurn());
        assertEquals(2, fx.player.consumablesUsedThisCombat());
    }

    @Test
    void combatCleanupResetsConsumableCounters() {
        TestFixture fx = TestFixture.basic();
        fx.player.consumablesUsedThisTurn(1);
        fx.player.consumablesUsedThisCombat(3);

        CombatCleanupOps.cleanupAfterCombatEnd(fx.state, fx.ctx);

        assertEquals(0, fx.player.consumablesUsedThisTurn());
        assertEquals(0, fx.player.consumablesUsedThisCombat());
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
    void criticalChanceComesFromKeywordHook() {
        TestFixture fx = TestFixture.basic();
        CardInstId critical = fx.addHandCard(fx.player, "CRITICAL_STRIKE");

        int chance = KeywordOps.criticalChancePercent(
                fx.state,
                fx.ctx,
                TargetRef.ofPlayer(fx.playerId),
                critical,
                TargetRef.ofEnemy(fx.enemyId),
                "damage"
        );

        assertEquals(10, chance);

        Rational multiplier = KeywordOps.criticalAmountMultiplier(
                fx.state,
                fx.ctx,
                TargetRef.ofPlayer(fx.playerId),
                critical,
                TargetRef.ofEnemy(fx.enemyId),
                "damage"
        );
        assertEquals(Rational.of(3, 2), multiplier);
    }


    @Test
    void keywordCriticalMultiplierUsesMaxNotProduct() {
        TestFixture fx = TestFixture.basic();
        CardInstId dual = fx.addHandCard(fx.player, "DUAL_CRITICAL_STRIKE");

        Rational multiplier = KeywordOps.criticalAmountMultiplier(
                fx.state,
                fx.ctx,
                TargetRef.ofPlayer(fx.playerId),
                dual,
                TargetRef.ofEnemy(fx.enemyId),
                "damage"
        );

        assertEquals(Rational.of(3, 2), multiplier);
    }

    @Test
    void rationalCriticalRoundingMatchesPreviousBehaviorForPositiveValues() {
        assertEquals(8, invokeMultiplyAndRound(5, Rational.of(3, 2)));
        assertEquals(6, invokeMultiplyAndRound(4, Rational.of(3, 2)));
    }

    @Test
    void criticalCanBeModifiedByStatusAndPassiveHooks() {
        TestFixture fx = TestFixture.basic();
        CardInstId critical = fx.addHandCard(fx.player, "CRITICAL_STRIKE");

        fx.player.statusSet(TestCriticalStatus.ID, 1);
        fx.player.addPassiveId(TestCriticalPassive.ID);

        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        int hpBefore = fx.enemy.hp();
        EngineResult play = fx.process(new PlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, critical,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));

        assertTrue(play.accepted());
        assertEquals(hpBefore - 20, fx.enemy.hp(), "critical multiplier should become x4");
        assertTrue(play.events().stream().anyMatch(e -> e instanceof GameEvent.LogAppended l && l.line().contains("critical! damage x4")));
    }

    @Test
    void criticalLogUsesExactRationalFormatting() {
        TestFixture fx = TestFixture.basic();
        CardInstId critical = fx.addHandCard(fx.player, "CRITICAL_STRIKE");

        fx.player.statusSet(TestCriticalChanceOnlyStatus.ID, 1);
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        int hpBefore = fx.enemy.hp();
        EngineResult play = fx.process(new PlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, critical,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));

        assertTrue(play.accepted());
        assertEquals(hpBefore - 8, fx.enemy.hp());
        assertTrue(play.events().stream().anyMatch(e -> e instanceof GameEvent.LogAppended l && l.line().contains("critical! damage x3/2")));
    }

    @Test
    void incomingCriticalHooksCanModifyReceivedCriticalDamage() {
        TestFixture fx = TestFixture.basic();
        CardInstId enemyCritical = fx.addEnemyHandCard("CRITICAL_STRIKE");

        fx.enemy.statusSet(TestCriticalStatus.ID, 1); // 치명 확률 100% 보장
        fx.player.statusSet(TestIncomingCriticalStatus.ID, 1);
        fx.player.addPassiveId(TestIncomingCriticalPassive.ID);

        fx.enemy.ap(3);
        fx.startSimpleCombat();
        fx.forceMainTurnForEnemy();

        int hpBefore = fx.player.hp();
        EngineResult play = fx.process(new EnemyPlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.enemyId, enemyCritical,
                new TargetSelection(List.of(TargetRef.ofPlayer(fx.playerId)))));

        assertTrue(play.accepted());
        assertEquals(hpBefore - 18, fx.player.hp(), "keyword x3/2 -> incoming passive +1 -> incoming status +1 => x7/2");
        assertTrue(play.events().stream().anyMatch(e -> e instanceof GameEvent.LogAppended l && l.line().contains("critical! damage x7/2")));
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
    void victoryPostStateClearsCombatContextAndCreatesRunResult() {
        TestFixture fx = TestFixture.basic();
        CardInstId strike = fx.addHandCard(fx.player, "NORMAL_STRIKE");

        PlayerId allyId = new PlayerId("P2");
        PlayerState ally = new PlayerState(allyId);
        fx.state.players().put(allyId, ally);
        ally.pendingDecision(new PendingDecision.SearchPick(
                "manual pending",
                List.of(strike),
                1,
                Zone.HAND,
                false,
                UUID.randomUUID()
        ));

        fx.enemy.hp(5);
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        EngineResult play = fx.process(new PlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, strike,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))));

        assertTrue(play.accepted());
        assertNull(fx.state.combat());
        assertEquals(NodeState.NON_COMBAT, fx.state.nodeState());
        assertNull(ally.pendingDecision(), "combat end should clear pending decisions for all players");
        assertFalse(fx.state.runState().recentResults().isEmpty(), "combat victory should append run recentResults");
        RunState.RecentResult result = fx.state.runState().recentResults().get(0);
        assertEquals("combat", result.type());
        assertEquals("전투 승리", result.summary());
    }

    @Test
    void defeatPostStateClearsCombatContextAndCreatesRunResult() {
        TestFixture fx = TestFixture.basic();
        CardInstId enemyCard = fx.addEnemyHandCard("NORMAL_STRIKE");
        fx.enemy.ap(3);
        fx.player.hp(5);

        fx.startSimpleCombat();
        fx.forceMainTurnForEnemy();

        EngineResult play = fx.process(new EnemyPlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.enemyId, enemyCard,
                new TargetSelection(List.of(TargetRef.ofPlayer(fx.playerId)))));

        assertTrue(play.accepted());
        assertEquals(0, fx.player.hp());
        assertNull(fx.state.combat());
        assertEquals(NodeState.NON_COMBAT, fx.state.nodeState());
        assertFalse(fx.state.runState().recentResults().isEmpty(), "combat defeat should append run recentResults");
        RunState.RecentResult result = fx.state.runState().recentResults().get(0);
        assertEquals("combat", result.type());
        assertEquals("전투 패배", result.summary());
    }

    @Test
    void combatVictoryRunLoopSetsResultPendingAndAllowsNextNodeSelectionAfterClear() {
        TestFixture fx = TestFixture.basic();
        CardInstId strike = fx.addHandCard(fx.player, "NORMAL_STRIKE");
        fx.enemy.hp(5);

        RunState.NodeChoice combatChoice = fx.state.runState().availableChoices().stream()
                .filter(choice -> !choice.disabled())
                .filter(choice -> choice.phase() == RunState.NodePhase.COMBAT)
                .findFirst()
                .orElseThrow();

        EngineResult selectNode = fx.process(new SelectNodeChoiceCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                combatChoice.id()
        ));
        assertTrue(selectNode.accepted());
        assertEquals(NodeState.COMBAT, fx.state.nodeState());

        EngineResult startCombat = fx.process(new StartCombatCommand(UUID.randomUUID(), fx.state.version(), fx.playerId));
        assertTrue(startCombat.accepted());
        assertNotNull(fx.state.combat());

        fx.forceMainTurnForPlayer();
        int hpBefore = fx.enemy.hp();
        EngineResult play = fx.process(new PlayCardCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                strike,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))
        ));

        assertTrue(play.accepted());
        assertEquals(hpBefore - 5, fx.enemy.hp(), "card target selection should reduce enemy HP");
        assertNull(fx.state.combat(), "combat context should end on victory");
        assertEquals(NodeState.NON_COMBAT, fx.state.nodeState(), "node state should return to NON_COMBAT after combat ends");
        assertTrue(fx.state.runState().resultPending(), "combat victory should set resultPending");
        assertFalse(fx.state.runState().recentResults().isEmpty(), "combat victory should create run recentResults");

        EngineResult clearResults = fx.process(new ClearRecentResultsCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId
        ));
        assertTrue(clearResults.accepted());
        assertFalse(fx.state.runState().resultPending());
        assertNull(fx.state.runState().currentNode());
        assertFalse(fx.state.runState().availableChoices().isEmpty(), "next node choices should open after clearing results");

        RunState.NodeChoice nextChoice = fx.state.runState().availableChoices().stream()
                .filter(choice -> !choice.disabled())
                .findFirst()
                .orElseThrow();
        EngineResult nextSelect = fx.process(new SelectNodeChoiceCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                nextChoice.id()
        ));
        assertTrue(nextSelect.accepted(), "node selection should be possible after result clear");
    }


    @Test
    void hpZeroBattleIncapacitationPersistsAcrossCombatRestart() {
        TestFixture fx = TestFixture.basic();
        fx.addDeckCards(fx.player, "FILLER", 6);
        CardInstId enemyCard = fx.addEnemyHandCard("NORMAL_STRIKE");
        fx.enemy.ap(3);
        fx.player.hp(5);

        fx.startSimpleCombat();
        fx.forceMainTurnForEnemy();

        EngineResult defeat = fx.process(new EnemyPlayCardCommand(UUID.randomUUID(), fx.state.version(), fx.enemyId, enemyCard,
                new TargetSelection(List.of(TargetRef.ofPlayer(fx.playerId)))));

        assertTrue(defeat.accepted());
        assertNull(fx.state.combat());
        assertEquals(1, fx.player.status(CombatStatuses.BATTLE_INCAPACITATED_PERSISTENT));
        assertTrue(CombatStatuses.isBattleIncapacitated(fx.player));

        EngineResult restart = fx.process(new StartCombatCommand(UUID.randomUUID(), fx.state.version(), fx.playerId));

        assertTrue(restart.accepted());
        assertEquals(1, fx.player.status(CombatStatuses.BATTLE_INCAPACITATED_PERSISTENT));
        assertNull(fx.state.combat(), "restart immediately re-resolves defeat when all players remain persistently incapacitated");
        assertEquals(0, fx.player.hand().size(), "persistently incapacitated players do not receive opening draw");
    }

    @Test
    void deckOutBattleIncapacitationDoesNotPersistAcrossCombatRestart() {
        TestFixture fx = TestFixture.basic();
        fx.addDeckCards(fx.player, "FILLER", 6);
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();
        fx.player.hand().clear();
        fx.player.deck().clear();
        fx.player.grave().clear();

        EngineResult draw = fx.process(new DrawCommand(UUID.randomUUID(), fx.state.version(), fx.playerId, 1));

        assertTrue(draw.accepted());
        assertNull(fx.state.combat());
        assertEquals(1, fx.player.status(CombatStatuses.BATTLE_INCAPACITATED));
        assertEquals(0, fx.player.status(CombatStatuses.BATTLE_INCAPACITATED_PERSISTENT));

        EngineResult restart = fx.process(new StartCombatCommand(UUID.randomUUID(), fx.state.version(), fx.playerId));

        assertTrue(restart.accepted());
        assertEquals(0, fx.player.status(CombatStatuses.BATTLE_INCAPACITATED));
        assertEquals(0, fx.player.status(CombatStatuses.BATTLE_INCAPACITATED_PERSISTENT));
        assertTrue(fx.state.combat().turnOrder().contains(TargetRef.ofPlayer(fx.playerId)));
        assertTrue(fx.player.hand().size() > 0, "combat-only incapacitation should not block opening draw");
    }

    @Test
    void combatRestartClearsPendingDecision() {
        TestFixture fx = TestFixture.basic();
        fx.state.combat(new CombatState());
        fx.player.pendingDecision(new PendingDecision.DiscardToHandLimit("manual test", 6));

        fx.state.combat().phase(CombatPhase.END);
        EngineResult restart = fx.process(new StartCombatCommand(UUID.randomUUID(), fx.state.version(), fx.playerId));

        assertTrue(restart.accepted());
        assertNull(fx.player.pendingDecision());
    }

    @Test
    void combatRestartRemovesActiveSummons() {
        TestFixture fx = TestFixture.basic();
        fx.state.combat(new CombatState());

        CardInstId sourceCard = fx.addCard(fx.player, "FILLER", Zone.FIELD);
        SummonInstId summonId = new SummonInstId(UUID.randomUUID());
        SummonState summon = new SummonState(summonId, fx.playerId, sourceCard, 5, 5, 1, 0, 1, false);

        fx.state.summons().put(summonId, summon);
        fx.player.activeSummons().add(summonId);
        fx.player.summonByCard().put(sourceCard, summonId);

        fx.state.combat().phase(CombatPhase.END);
        EngineResult restart = fx.process(new StartCombatCommand(UUID.randomUUID(), fx.state.version(), fx.playerId));

        assertTrue(restart.accepted());
        assertFalse(fx.state.summons().containsKey(summonId));
        assertTrue(fx.player.activeSummons().isEmpty());
        assertTrue(fx.player.summonByCard().isEmpty());
    }

    @Test
    void summonActionDamageUsesSummonAttackPowerNotPlayerAttackPower() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        CardInstId summonCard = fx.addCard(fx.player, "SUMMON_SCALE_ATTACK", Zone.FIELD);
        SummonInstId summonId = new SummonInstId(UUID.randomUUID());
        SummonState summon = new SummonState(summonId, fx.playerId, summonCard, 5, 5, 2, 0, 1, false);
        fx.state.summons().put(summonId, summon);
        fx.player.activeSummons().add(summonId);
        fx.player.summonByCard().put(summonCard, summonId);

        int hpBefore = fx.enemy.hp();
        EngineResult use = fx.process(new UseSummonActionCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                summonId,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))
        ));

        assertTrue(use.accepted());
        assertEquals(hpBefore - 2, fx.enemy.hp(), "summon action should use summon atk");
        assertNotEquals(hpBefore - fx.player.attackPower(), fx.enemy.hp(), "must not use player atk");
    }

    @Test
    void summonActionHealUsesSummonHealPowerNotPlayerHealPower() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        CardInstId summonCard = fx.addCard(fx.player, "SUMMON_SCALE_HEAL", Zone.FIELD);
        SummonInstId summonId = new SummonInstId(UUID.randomUUID());
        SummonState summon = new SummonState(summonId, fx.playerId, summonCard, 5, 5, 0, 3, 1, false);
        fx.state.summons().put(summonId, summon);
        fx.player.activeSummons().add(summonId);
        fx.player.summonByCard().put(summonCard, summonId);

        fx.player.hp(fx.player.maxHp() - 10);
        int hpBefore = fx.player.hp();
        EngineResult use = fx.process(new UseSummonActionCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                summonId,
                new TargetSelection(List.of(TargetRef.ofPlayer(fx.playerId)))
        ));

        assertTrue(use.accepted());
        assertEquals(hpBefore + 3, fx.player.hp(), "summon action should use summon heal");
        assertNotEquals(hpBefore + fx.player.healPower(), fx.player.hp(), "must not use player heal");
    }

    @Test
    void summonActionSelfStatusTargetsSummonNotPlayer() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        CardInstId summonCard = fx.addCard(fx.player, "SUMMON_SELF_WEAK", Zone.FIELD);
        SummonInstId summonId = new SummonInstId(UUID.randomUUID());
        SummonState summon = new SummonState(summonId, fx.playerId, summonCard, 5, 5, 2, 0, 1, false);
        fx.state.summons().put(summonId, summon);
        fx.player.activeSummons().add(summonId);
        fx.player.summonByCard().put(summonCard, summonId);

        EngineResult use = fx.process(new UseSummonActionCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                summonId,
                TargetSelection.empty()
        ));

        assertTrue(use.accepted());
        assertEquals(2, summon.statusValues().getOrDefault(S105_Weak.ID, 0));
        assertTrue(summon.statusValues().containsKey(S105_Weak.ID));
        assertEquals(0, fx.player.status(S105_Weak.ID));
        assertFalse(fx.player.statusValues().containsKey(S105_Weak.ID));
    }

    @Test
    void summonActionSelfHealTargetsSummonNotPlayer() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        CardInstId summonCard = fx.addCard(fx.player, "SUMMON_SELF_HEAL_FIXED", Zone.FIELD);
        SummonInstId summonId = new SummonInstId(UUID.randomUUID());
        SummonState summon = new SummonState(summonId, fx.playerId, summonCard, 2, 8, 0, 0, 1, false);
        fx.state.summons().put(summonId, summon);
        fx.player.activeSummons().add(summonId);
        fx.player.summonByCard().put(summonCard, summonId);

        fx.player.hp(fx.player.maxHp() - 5);
        int playerHpBefore = fx.player.hp();
        int summonHpBefore = summon.hp();

        EngineResult use = fx.process(new UseSummonActionCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                summonId,
                TargetSelection.empty()
        ));

        assertTrue(use.accepted());
        assertEquals(summonHpBefore + 3, summon.hp());
        assertEquals(playerHpBefore, fx.player.hp());
    }

    @Test
    void summonActionUsesSummonAsSourceForOutgoingStatusHook() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        CardInstId summonCard = fx.addCard(fx.player, "SUMMON_SCALE_ATTACK", Zone.FIELD);
        SummonInstId summonId = new SummonInstId(UUID.randomUUID());
        SummonState summon = new SummonState(summonId, fx.playerId, summonCard, 5, 5, 5, 0, 1, false);
        summon.statusSet(S105_Weak.ID, 2);
        fx.state.summons().put(summonId, summon);
        fx.player.activeSummons().add(summonId);
        fx.player.summonByCard().put(summonCard, summonId);

        int hpBefore = fx.enemy.hp();
        EngineResult use = fx.process(new UseSummonActionCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                summonId,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))
        ));

        assertTrue(use.accepted());
        assertEquals(hpBefore - 3, fx.enemy.hp(), "weak on summon should reduce summon action damage");
    }

    @Test
    void summonIncomingDamageStatusHooksApplyToSummonTarget() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        SummonInstId summonId = fx.addSummon(fx.playerId, 12, 20);
        SummonState summon = fx.state.summons().get(summonId);
        assertNotNull(summon);

        summon.statusSet(S106_Vulnerable.ID, 2);
        summon.statusSet(S001_Shield.ID, 3);
        int hpBefore = summon.hp();

        DamageOps.apply(
                fx.state,
                fx.ctx,
                new ArrayList<>(),
                TargetRef.ofEnemy(fx.enemyId),
                "TEST_SUMMON_INCOMING",
                TargetRef.ofSummon(fx.playerId, summonId),
                5
        );

        assertEquals(hpBefore - 4, summon.hp(), "vulnerable(+2) and shield(3) should be applied on summon incoming damage");
        assertEquals(0, summon.statusValues().getOrDefault(S001_Shield.ID, 0), "shield should be consumed on summon first");
    }

    @Test
    void playerTurnEndProcessesSummonTurnStatuses() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        SummonInstId summonId = fx.addSummon(fx.playerId, 10, 20);
        SummonState summon = fx.state.summons().get(summonId);
        assertNotNull(summon);

        summon.statusSet(S002_Regeneration.ID, 6);
        summon.statusSet(S101_Pain.ID, 4);
        int hpBefore = summon.hp();

        EngineResult end = fx.process(new EndTurnCommand(UUID.randomUUID(), fx.state.version(), fx.playerId));

        assertTrue(end.accepted());
        assertEquals(hpBefore + 2, summon.hp(), "summon regen/pain turn-end hooks should run on owner turn end");
        assertEquals(3, summon.statusValues().getOrDefault(S002_Regeneration.ID, 0));
        assertEquals(2, summon.statusValues().getOrDefault(S101_Pain.ID, 0));
    }

    @Test
    void playerIncomingDamageStatusHookRegressionStillWorks() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        fx.player.statusSet(S001_Shield.ID, 3);
        int hpBefore = fx.player.hp();

        DamageOps.apply(
                fx.state,
                fx.ctx,
                new ArrayList<>(),
                TargetRef.ofEnemy(fx.enemyId),
                "TEST_PLAYER_INCOMING_REGRESSION",
                TargetRef.ofPlayer(fx.playerId),
                5
        );

        assertEquals(hpBefore - 2, fx.player.hp(), "player shield should still absorb incoming damage");
        assertEquals(0, fx.player.status(S001_Shield.ID));
    }

    @Test
    void playCardSelfHealStillTargetsPlayer() {
        TestFixture fx = TestFixture.basic();
        CardInstId cardId = fx.addHandCard(fx.player, "SUMMON_SELF_HEAL_FIXED");
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        fx.player.hp(fx.player.maxHp() - 5);
        int hpBefore = fx.player.hp();
        EngineResult play = fx.process(new PlayCardCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                cardId,
                TargetSelection.empty()
        ));

        assertTrue(play.accepted());
        assertEquals(hpBefore + 3, fx.player.hp());
    }

    @Test
    void playCardStillUsesPlayerStatsForActorScaling() {
        TestFixture fx = TestFixture.basic();
        CardInstId cardId = fx.addHandCard(fx.player, "SUMMON_SCALE_ATTACK");
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        int hpBefore = fx.enemy.hp();
        EngineResult play = fx.process(new PlayCardCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                cardId,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))
        ));

        assertTrue(play.accepted());
        assertEquals(hpBefore - fx.player.attackPower(), fx.enemy.hp(), "normal card path should keep player atk scaling");
    }

    @Test
    void summonActionCanOnlyBeUsedOncePerTurn() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();

        CardInstId summonCard = fx.addCard(fx.player, "SUMMON_SCALE_ATTACK", Zone.FIELD);
        SummonInstId summonId = new SummonInstId(UUID.randomUUID());
        SummonState summon = new SummonState(summonId, fx.playerId, summonCard, 5, 5, 2, 0, 1, false);
        fx.state.summons().put(summonId, summon);
        fx.player.activeSummons().add(summonId);
        fx.player.summonByCard().put(summonCard, summonId);

        EngineResult first = fx.process(new UseSummonActionCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                summonId,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))
        ));
        assertTrue(first.accepted());

        EngineResult second = fx.process(new UseSummonActionCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                summonId,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))
        ));
        assertFalse(second.accepted());
        assertTrue(second.errors().contains("summon action already used this turn"));
    }

    @Test
    void enemyOneAllowsEnemyBodyAndEnemySummonSelection() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();
        fx.player.ap(10);

        CardInstId hit = fx.addHandCard(fx.player, "ENEMY_ONE_HIT_FIXED");
        PlayerId enemyOwner = new PlayerId(fx.enemyId.value());
        SummonInstId enemySummonId = fx.addSummon(enemyOwner, 6, 6);
        SummonState enemySummon = fx.state.summons().get(enemySummonId);

        int enemyHpBefore = fx.enemy.hp();
        int enemySummonHpBefore = enemySummon.hp();

        EngineResult targetSummon = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, hit,
                new TargetSelection(List.of(TargetRef.ofSummon(enemyOwner, enemySummonId)))
        ));
        assertTrue(targetSummon.accepted());
        assertEquals(enemyHpBefore, fx.enemy.hp());
        assertEquals(enemySummonHpBefore - 2, enemySummon.hp());

        CardInstId hitBody = fx.addHandCard(fx.player, "ENEMY_ONE_HIT_FIXED");
        EngineResult targetEnemy = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, hitBody,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))
        ));
        assertTrue(targetEnemy.accepted());
        assertEquals(enemyHpBefore - 2, fx.enemy.hp());
    }

    @Test
    void enemyOneTauntOnEnemySummonForcesSummonAsFinalTarget() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();
        fx.player.ap(10);

        PlayerId enemyOwner = new PlayerId(fx.enemyId.value());
        SummonInstId enemySummonId = fx.addSummon(enemyOwner, 6, 6);
        SummonState enemySummon = fx.state.summons().get(enemySummonId);
        enemySummon.statusSet(S005_Taunt.ID, 1);

        int enemyHpBefore = fx.enemy.hp();
        int summonHpBefore = enemySummon.hp();
        CardInstId hit = fx.addHandCard(fx.player, "ENEMY_ONE_HIT_FIXED");

        EngineResult blocked = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, hit,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))
        ));

        assertFalse(blocked.accepted(), "validate should reject enemy-body choice when summon taunt exists");
        assertTrue(blocked.errors().stream().anyMatch(e -> e.contains("taunt: must target one of")));
        assertEquals(enemyHpBefore, fx.enemy.hp());
        assertEquals(summonHpBefore, enemySummon.hp());

        CardInstId forcedHit = fx.addHandCard(fx.player, "ENEMY_ONE_HIT_FIXED");
        EngineResult forced = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, forcedHit,
                new TargetSelection(List.of(TargetRef.ofSummon(enemyOwner, enemySummonId)))
        ));
        assertTrue(forced.accepted(), "selecting taunt summon should pass and hit summon");
        assertEquals(enemyHpBefore, fx.enemy.hp(), "enemy body should not be hit when summon has taunt");
        assertEquals(summonHpBefore - 2, enemySummon.hp(), "taunt summon should be final target");
    }

    @Test
    void enemyOneTauntOnEnemyBodyStillForcesEnemyBody() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();
        fx.player.ap(10);

        fx.enemy.statusSet(S005_Taunt.ID, 1);
        PlayerId enemyOwner = new PlayerId(fx.enemyId.value());
        SummonInstId enemySummonId = fx.addSummon(enemyOwner, 6, 6);
        SummonState enemySummon = fx.state.summons().get(enemySummonId);

        int enemyHpBefore = fx.enemy.hp();
        int summonHpBefore = enemySummon.hp();
        CardInstId hit = fx.addHandCard(fx.player, "ENEMY_ONE_HIT_FIXED");

        EngineResult play = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, hit,
                new TargetSelection(List.of(TargetRef.ofSummon(enemyOwner, enemySummonId)))
        ));

        assertFalse(play.accepted(), "validate should reject non-taunt target");
        assertTrue(play.errors().stream().anyMatch(e -> e.contains("taunt: must target one of")));
        assertEquals(enemyHpBefore, fx.enemy.hp());
        assertEquals(summonHpBefore, enemySummon.hp());
    }

    @Test
    void enemyOneConfusionIgnoresTauntAndCanRedirectToFullCandidateSet() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();
        fx.player.ap(10);

        PlayerId enemyOwner = new PlayerId(fx.enemyId.value());
        SummonInstId allySummonId = fx.addSummon(fx.playerId, 6, 6);
        SummonInstId enemySummonId = fx.addSummon(enemyOwner, 6, 6);
        SummonState allySummon = fx.state.summons().get(allySummonId);
        SummonState enemySummon = fx.state.summons().get(enemySummonId);

        fx.enemy.statusSet(S005_Taunt.ID, 2);
        fx.player.statusSet(S107_Confusion.ID, 1);

        int playerHpBefore = fx.player.hp();
        int enemyHpBefore = fx.enemy.hp();
        int allySummonHpBefore = allySummon.hp();
        int enemySummonHpBefore = enemySummon.hp();
        CardInstId hit = fx.addHandCard(fx.player, "ENEMY_ONE_HIT_FIXED");

        EngineResult play = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, hit,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))
        ));

        assertTrue(play.accepted(), "confusion should bypass taunt validation");
        int changed = 0;
        if (fx.player.hp() != playerHpBefore) changed++;
        if (fx.enemy.hp() != enemyHpBefore) changed++;
        if (allySummon.hp() != allySummonHpBefore) changed++;
        if (enemySummon.hp() != enemySummonHpBefore) changed++;
        assertEquals(1, changed, "confusion redirect should apply to exactly one full-candidate target");
        assertEquals(0, fx.player.statusValues().getOrDefault(S107_Confusion.ID, 0));
    }

    @Test
    void anyOneEnemyOrSummonUsesEnemyOneSpecialRulesButPlayerSelectionDoesNot() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();
        fx.player.ap(10);

        PlayerId enemyOwner = new PlayerId(fx.enemyId.value());
        SummonInstId enemySummonId = fx.addSummon(enemyOwner, 6, 6);
        SummonState enemySummon = fx.state.summons().get(enemySummonId);
        enemySummon.statusSet(S005_Taunt.ID, 1);

        int enemyHpBefore = fx.enemy.hp();
        int summonHpBefore = enemySummon.hp();
        CardInstId anyHit = fx.addHandCard(fx.player, "ANY_ONE_HIT_FIXED");

        EngineResult toEnemy = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, anyHit,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))
        ));
        assertFalse(toEnemy.accepted(), "ANY_ONE enemy selection should still run taunt validation");
        assertTrue(toEnemy.errors().stream().anyMatch(e -> e.contains("taunt: must target one of")));
        assertEquals(enemyHpBefore, fx.enemy.hp(), "blocked validation should keep hp");
        assertEquals(summonHpBefore, enemySummon.hp());

        CardInstId anyHitSummon = fx.addHandCard(fx.player, "ANY_ONE_HIT_FIXED");
        EngineResult toSummon = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, anyHitSummon,
                new TargetSelection(List.of(TargetRef.ofSummon(enemyOwner, enemySummonId)))
        ));
        assertTrue(toSummon.accepted());
        assertEquals(enemyHpBefore, fx.enemy.hp(), "ANY_ONE summon selection should stay on summon");
        assertEquals(summonHpBefore - 2, enemySummon.hp());

        CardInstId anyHitPlayer = fx.addHandCard(fx.player, "ANY_ONE_HIT_FIXED");
        int playerHpBefore = fx.player.hp();
        EngineResult toPlayer = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, anyHitPlayer,
                new TargetSelection(List.of(TargetRef.ofPlayer(fx.playerId)))
        ));
        assertTrue(toPlayer.accepted(), "ANY_ONE player selection should bypass enemy-only special rules");
        assertEquals(playerHpBefore - 2, fx.player.hp());
    }

    @Test
    void clearMindStillIgnoresTauntForEnemyOneAndAnyOneEnemySelection() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();
        fx.player.ap(10);

        PlayerId enemyOwner = new PlayerId(fx.enemyId.value());
        SummonInstId enemySummonId = fx.addSummon(enemyOwner, 6, 6);
        SummonState enemySummon = fx.state.summons().get(enemySummonId);
        enemySummon.statusSet(S005_Taunt.ID, 2);

        int enemyHpBefore = fx.enemy.hp();
        int summonHpBefore = enemySummon.hp();

        CardInstId enemyOneClearMind = fx.addHandCard(fx.player, "ENEMY_ONE_CLEAR_MIND_HIT");
        EngineResult enemyOnePlay = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, enemyOneClearMind,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))
        ));
        assertTrue(enemyOnePlay.accepted());
        assertEquals(enemyHpBefore - 2, fx.enemy.hp());
        assertEquals(summonHpBefore, enemySummon.hp(), "clear mind should ignore taunt on enemy summon");

        CardInstId anyOneClearMind = fx.addHandCard(fx.player, "ANY_ONE_CLEAR_MIND_HIT");
        EngineResult anyOnePlay = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, anyOneClearMind,
                new TargetSelection(List.of(TargetRef.ofEnemy(fx.enemyId)))
        ));
        assertTrue(anyOnePlay.accepted());
        assertEquals(enemyHpBefore - 4, fx.enemy.hp());
        assertEquals(summonHpBefore, enemySummon.hp());
    }

    @Test
    void enemyAllAndSideIncludeEnemyBodyAndAllEnemySummonsWithoutDuplicates() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();
        fx.player.ap(10);

        PlayerId enemyOwner = new PlayerId(fx.enemyId.value());
        SummonInstId enemySummonId = fx.addSummon(enemyOwner, 10, 10);
        SummonState enemySummon = fx.state.summons().get(enemySummonId);
        int enemyHpBefore = fx.enemy.hp();
        int enemySummonHpBefore = enemySummon.hp();

        CardInstId enemyAll = fx.addHandCard(fx.player, "ENEMY_ALL_HIT");
        EngineResult all = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, enemyAll, TargetSelection.empty()
        ));
        assertTrue(all.accepted());
        assertEquals(enemyHpBefore - 2, fx.enemy.hp());
        assertEquals(enemySummonHpBefore - 2, enemySummon.hp());

        CardInstId enemySide = fx.addHandCard(fx.player, "ENEMY_SIDE_HIT");
        EngineResult side = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, enemySide, TargetSelection.empty()
        ));
        assertTrue(side.accepted());
        assertEquals(enemyHpBefore - 5, fx.enemy.hp());
        assertEquals(enemySummonHpBefore - 5, enemySummon.hp());
    }

    @Test
    void allyOneAllowsSelectingPlayerAndAllySummon() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();
        fx.player.ap(10);

        SummonInstId allySummonId = fx.addSummon(fx.playerId, 3, 8);
        SummonState allySummon = fx.state.summons().get(allySummonId);
        fx.player.hp(fx.player.hp() - 5);
        allySummon.hp(allySummon.hp() - 2);
        int playerHpBefore = fx.player.hp();
        int summonHpBefore = allySummon.hp();

        CardInstId healPlayer = fx.addHandCard(fx.player, "ALLY_ONE_HEAL_FIXED");
        EngineResult toPlayer = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, healPlayer,
                new TargetSelection(List.of(TargetRef.ofPlayer(fx.playerId)))
        ));
        assertTrue(toPlayer.accepted());
        assertEquals(playerHpBefore + 2, fx.player.hp());
        assertEquals(summonHpBefore, allySummon.hp());

        CardInstId healSummon = fx.addHandCard(fx.player, "ALLY_ONE_HEAL_FIXED");
        EngineResult toSummon = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, healSummon,
                new TargetSelection(List.of(TargetRef.ofSummon(fx.playerId, allySummonId)))
        ));
        assertTrue(toSummon.accepted());
        assertEquals(summonHpBefore + 2, allySummon.hp());
    }

    @Test
    void allyAllAndSideIncludePlayerAndAllAllySummonsWithoutDuplicates() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();
        fx.player.ap(10);

        SummonInstId allySummonId = fx.addSummon(fx.playerId, 5, 10);
        SummonState allySummon = fx.state.summons().get(allySummonId);
        fx.player.hp(fx.player.hp() - 8);
        allySummon.hp(allySummon.hp() - 4);
        int playerHpBefore = fx.player.hp();
        int summonHpBefore = allySummon.hp();

        CardInstId allyAll = fx.addHandCard(fx.player, "ALLY_ALL_HEAL_FIXED");
        EngineResult all = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, allyAll, TargetSelection.empty()
        ));
        assertTrue(all.accepted());
        assertEquals(playerHpBefore + 2, fx.player.hp());
        assertEquals(summonHpBefore + 2, allySummon.hp());

        CardInstId allySide = fx.addHandCard(fx.player, "ALLY_SIDE_HEAL_FIXED");
        EngineResult side = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, allySide, TargetSelection.empty()
        ));
        assertTrue(side.accepted());
        assertEquals(playerHpBefore + 5, fx.player.hp());
        assertEquals(summonHpBefore + 5, allySummon.hp());
    }

    @Test
    void allAndSideTargetsKeepPreviousBehaviorWhenNoSummonsExist() {
        TestFixture fx = TestFixture.basic();
        fx.startSimpleCombat();
        fx.forceMainTurnForPlayer();
        fx.player.ap(10);

        fx.player.hp(fx.player.hp() - 4);
        int playerHpBefore = fx.player.hp();
        int enemyHpBefore = fx.enemy.hp();

        CardInstId enemyAll = fx.addHandCard(fx.player, "ENEMY_ALL_HIT");
        EngineResult damage = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, enemyAll, TargetSelection.empty()
        ));
        assertTrue(damage.accepted());
        assertEquals(enemyHpBefore - 2, fx.enemy.hp());
        assertEquals(playerHpBefore, fx.player.hp());

        CardInstId allyAll = fx.addHandCard(fx.player, "ALLY_ALL_HEAL_FIXED");
        EngineResult heal = fx.process(new PlayCardCommand(
                UUID.randomUUID(), fx.state.version(), fx.playerId, allyAll, TargetSelection.empty()
        ));
        assertTrue(heal.accepted());
        assertEquals(playerHpBefore + 2, fx.player.hp());
    }

    @Test
    void combatRestartGathersNonExOwnedCardsFromCombatZonesToDeck() {
        TestFixture fx = TestFixture.basic();
        fx.state.combat(new CombatState());

        CardInstId hand = fx.addCard(fx.player, "FILLER", Zone.HAND);
        CardInstId grave = fx.addCard(fx.player, "FILLER", Zone.GRAVE);
        CardInstId field = fx.addCard(fx.player, "FILLER", Zone.FIELD);
        CardInstId excluded = fx.addCard(fx.player, "FILLER", Zone.EXCLUDED);
        CardInstId ex = fx.addCard(fx.player, "EX_BLAST", Zone.EX);

        fx.player.statusSet(CombatStatuses.BATTLE_INCAPACITATED_PERSISTENT, 1);

        fx.state.combat().phase(CombatPhase.END);
        EngineResult restart = fx.process(new StartCombatCommand(UUID.randomUUID(), fx.state.version(), fx.playerId));

        assertTrue(restart.accepted());

        assertTrue(fx.player.hand().isEmpty());
        assertTrue(fx.player.grave().isEmpty());
        assertTrue(fx.player.field().isEmpty());
        assertTrue(fx.player.excluded().isEmpty());

        assertTrue(fx.player.deck().contains(hand));
        assertTrue(fx.player.deck().contains(grave));
        assertTrue(fx.player.deck().contains(field));
        assertTrue(fx.player.deck().contains(excluded));

        assertEquals(Zone.DECK, fx.state.card(hand).zone());
        assertEquals(Zone.DECK, fx.state.card(grave).zone());
        assertEquals(Zone.DECK, fx.state.card(field).zone());
        assertEquals(Zone.DECK, fx.state.card(excluded).zone());

        assertEquals(Zone.EX, fx.state.card(ex).zone());
        assertEquals(ex, fx.player.exCard());
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


    private static int invokeMultiplyAndRound(int amount, Rational multiplier) {
        return TestCardEffect.multiplyAndRound(amount, multiplier);
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
            registerCard(defs, effects, new TestCardEffect("CRITICAL_STRIKE", CardType.SKILL, 1, Map.of(K011_Critical.ID, 2), 5));
            registerCard(defs, effects, new TestCardEffect("DUAL_CRITICAL_STRIKE", CardType.SKILL, 1, Map.of(K011_Critical.ID, 2, "FAKE_CRIT_HALF", 1), 5));
            registerCard(defs, effects, new TestCardEffect("EX_BLAST", CardType.EX, 1, Map.of(), 4));
            registerCard(defs, effects, new ScaleWithActorAttackCardEffect("SUMMON_SCALE_ATTACK"));
            registerCard(defs, effects, new ScaleWithActorHealCardEffect("SUMMON_SCALE_HEAL"));
            registerCard(defs, effects, new SelfStatusCardEffect("SUMMON_SELF_WEAK"));
            registerCard(defs, effects, new SelfFixedHealCardEffect("SUMMON_SELF_HEAL_FIXED"));
            registerCard(defs, effects, new TargetFixedValueCardEffect("ENEMY_ONE_HIT_FIXED", Target.ENEMY_ONE, true, 2));
            registerCard(defs, effects, new TargetFixedValueCardEffect("ANY_ONE_HIT_FIXED", Target.ANY_ONE, true, 2));
            registerCard(defs, effects, new TargetFixedValueCardEffect("ENEMY_ONE_CLEAR_MIND_HIT", Target.ENEMY_ONE, true, 2, Map.of(K007_ClearMind.ID, 1)));
            registerCard(defs, effects, new TargetFixedValueCardEffect("ANY_ONE_CLEAR_MIND_HIT", Target.ANY_ONE, true, 2, Map.of(K007_ClearMind.ID, 1)));
            registerCard(defs, effects, new TargetFixedValueCardEffect("ENEMY_ALL_HIT", Target.ENEMY_ALL, true, 2));
            registerCard(defs, effects, new TargetFixedValueCardEffect("ENEMY_SIDE_HIT", Target.ENEMY_SIDE, true, 3));
            registerCard(defs, effects, new TargetFixedValueCardEffect("ALLY_ONE_HEAL_FIXED", Target.ALLY_ONE, false, 2));
            registerCard(defs, effects, new TargetFixedValueCardEffect("ALLY_ALL_HEAL_FIXED", Target.ALLY_ALL, false, 2));
            registerCard(defs, effects, new TargetFixedValueCardEffect("ALLY_SIDE_HEAL_FIXED", Target.ALLY_SIDE, false, 3));

            Map<String, StatusDefinition> statusDefs = new HashMap<>();
            Map<String, com.example.dueltower.engine.core.effect.status.StatusEffect> statusEffects = new HashMap<>();
            for (StatusBlueprint bp : List.of(
                    new S001_Shield(), new S002_Regeneration(), new S004_Evasion(), new S005_Taunt(),
                    new S101_Pain(), new S102_Stun(), new S103_Pressure(), new S104_Destruction(),
                    new S105_Weak(), new S106_Vulnerable(), new S107_Confusion(), new S108_Seal(), new S301_Barrier(),
                    new TestCriticalStatus(), new TestIncomingCriticalStatus(), new TestCriticalChanceOnlyStatus()
            )) {
                statusDefs.put(bp.id(), bp.definition());
                statusEffects.put(bp.id(), bp);
            }

            Map<String, KeywordDefinition> keywordDefs = new HashMap<>();
            Map<String, com.example.dueltower.engine.core.effect.keyword.KeywordEffect> keywordEffects = new HashMap<>();
            for (var bp : List.of(new K007_ClearMind(), new K008_Accurate(), new K009_Penetration(), new K010_Tenacity(), new K011_Critical(), new FakeCriticalHalfKeyword())) {
                keywordDefs.put(bp.id(), bp.definition());
                keywordEffects.put(bp.id(), bp);
            }

            Map<String, PassiveDefinition> passiveDefs = new HashMap<>();
            Map<String, com.example.dueltower.engine.core.effect.passive.PassiveEffect> passiveEffects = new HashMap<>();
            TestCriticalPassive criticalPassive = new TestCriticalPassive();
            passiveDefs.put(TestCriticalPassive.ID, TestCriticalPassive.definition());
            passiveEffects.put(TestCriticalPassive.ID, criticalPassive);

            TestIncomingCriticalPassive incomingCriticalPassive = new TestIncomingCriticalPassive();
            passiveDefs.put(TestIncomingCriticalPassive.ID, TestIncomingCriticalPassive.definition());
            passiveEffects.put(TestIncomingCriticalPassive.ID, incomingCriticalPassive);

            this.ctx = new EngineContext(defs, effects, statusDefs, statusEffects, keywordDefs, keywordEffects, passiveDefs, passiveEffects);
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

        SummonInstId addSummon(PlayerId owner, int hp, int maxHp) {
            SummonInstId summonId = new SummonInstId(UUID.randomUUID());
            PlayerState ownerState = state.player(owner);
            CardInstId sourceCardId;
            SummonState summon;
            if (ownerState != null) {
                sourceCardId = addCard(ownerState, "FILLER", Zone.FIELD);
                summon = new SummonState(summonId, owner, sourceCardId, hp, maxHp, 1, 1, 1, false);
                state.summons().put(summonId, summon);
                ownerState.activeSummons().add(summonId);
                ownerState.summonByCard().put(sourceCardId, summonId);
            } else {
                sourceCardId = Ids.newCardInstId();
                summon = new SummonState(summonId, owner, sourceCardId, hp, maxHp, 1, 1, 1, false);
                state.summons().put(summonId, summon);
            }
            return summonId;
        }

        private static void registerCard(Map<CardDefId, CardDefinition> defs, Map<CardDefId, CardEffect> effects, TestCardEffect effect) {
            CardDefinition def = effect.definition();
            defs.put(def.id(), def);
            effects.put(def.id(), effect);
        }

        private static void registerCard(Map<CardDefId, CardDefinition> defs, Map<CardDefId, CardEffect> effects, ScaleWithActorAttackCardEffect effect) {
            CardDefinition def = effect.definition();
            defs.put(def.id(), def);
            effects.put(def.id(), effect);
        }

        private static void registerCard(Map<CardDefId, CardDefinition> defs, Map<CardDefId, CardEffect> effects, ScaleWithActorHealCardEffect effect) {
            CardDefinition def = effect.definition();
            defs.put(def.id(), def);
            effects.put(def.id(), effect);
        }

        private static void registerCard(Map<CardDefId, CardDefinition> defs, Map<CardDefId, CardEffect> effects, SelfStatusCardEffect effect) {
            CardDefinition def = effect.definition();
            defs.put(def.id(), def);
            effects.put(def.id(), effect);
        }

        private static void registerCard(Map<CardDefId, CardDefinition> defs, Map<CardDefId, CardEffect> effects, SelfFixedHealCardEffect effect) {
            CardDefinition def = effect.definition();
            defs.put(def.id(), def);
            effects.put(def.id(), effect);
        }

        private static void registerCard(Map<CardDefId, CardDefinition> defs, Map<CardDefId, CardEffect> effects, TargetFixedValueCardEffect effect) {
            CardDefinition def = effect.definition();
            defs.put(def.id(), def);
            effects.put(def.id(), effect);
        }
    }


    private static final class FakeCriticalHalfKeyword implements com.example.dueltower.content.keyword.model.KeywordBlueprint {
        @Override
        public String id() {
            return "FAKE_CRIT_HALF";
        }

        @Override
        public KeywordDefinition definition() {
            return new KeywordDefinition(id(), "가짜 반치명", true, "테스트용 치명 절반");
        }

        @Override
        public Rational criticalAmountMultiplier(KeywordRuntime rt, DamageKeywordCtx c, String kind) {
            return rt.value() > 0 ? Rational.of(4, 3) : Rational.ONE;
        }
    }

    private static final class TestCriticalChanceOnlyStatus implements com.example.dueltower.content.status.model.StatusBlueprint {
        static final String ID = "TEST_CRITICAL_CHANCE_ONLY_STATUS";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public StatusDefinition definition() {
            return new StatusDefinition(ID, "치명 확률 상태", StatusKind.BUFF, StatusScope.CHARACTER, Set.of(), 10, false, "치명 확률만 증가시킨다.");
        }

        @Override
        public int onCriticalChancePercent(com.example.dueltower.engine.core.effect.status.StatusRuntime rt,
                                           StatusOwnerRef owner,
                                           TargetRef source,
                                           TargetRef target,
                                           String kind,
                                           int currentChance) {
            return currentChance + 90;
        }
    }

    private static final class TestCriticalStatus implements com.example.dueltower.content.status.model.StatusBlueprint {
        static final String ID = "TEST_CRITICAL_STATUS";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public StatusDefinition definition() {
            return new StatusDefinition(ID, "치명 증폭 상태", StatusKind.BUFF, StatusScope.CHARACTER, Set.of(), 10, false, "치명 확률/배율을 증가시킨다.");
        }

        @Override
        public int onCriticalChancePercent(com.example.dueltower.engine.core.effect.status.StatusRuntime rt,
                                           StatusOwnerRef owner,
                                           TargetRef source,
                                           TargetRef target,
                                           String kind,
                                           int currentChance) {
            return currentChance + 90;
        }

        @Override
        public Rational onCriticalAmountMultiplier(com.example.dueltower.engine.core.effect.status.StatusRuntime rt,
                                              StatusOwnerRef owner,
                                              TargetRef source,
                                              TargetRef target,
                                              String kind,
                                              Rational currentMultiplier) {
            return Rational.max(currentMultiplier, Rational.of(3));
        }
    }

    private static final class TestCriticalPassive implements com.example.dueltower.engine.core.effect.passive.PassiveEffect {
        static final String ID = "TEST_CRITICAL_PASSIVE";

        @Override
        public String id() {
            return ID;
        }

        static PassiveDefinition definition() {
            return new PassiveDefinition(ID, "치명 증폭 패시브", 10, "치명 확률과 배율을 올린다.");
        }

        @Override
        public int onCriticalChancePercent(com.example.dueltower.engine.core.effect.passive.PassiveRuntime rt,
                                           TargetRef source,
                                           TargetRef target,
                                           String kind,
                                           int currentChance) {
            return currentChance + 60;
        }

        @Override
        public Rational onCriticalAmountMultiplier(com.example.dueltower.engine.core.effect.passive.PassiveRuntime rt,
                                              TargetRef source,
                                              TargetRef target,
                                              String kind,
                                              Rational currentMultiplier) {
            return currentMultiplier.add(1);
        }
    }

    private static final class TestIncomingCriticalStatus implements com.example.dueltower.content.status.model.StatusBlueprint {
        static final String ID = "TEST_INCOMING_CRITICAL_STATUS";

        @Override
        public String id() {
            return ID;
        }

        @Override
        public StatusDefinition definition() {
            return new StatusDefinition(ID, "피격 치명 증폭 상태", StatusKind.BUFF, StatusScope.CHARACTER, Set.of(), 10, false, "받는 치명 배율을 증가시킨다.");
        }

        @Override
        public Rational onIncomingCriticalAmountMultiplier(com.example.dueltower.engine.core.effect.status.StatusRuntime rt,
                                                      StatusOwnerRef owner,
                                                      TargetRef source,
                                                      TargetRef target,
                                                      String kind,
                                                      Rational currentMultiplier) {
            return currentMultiplier.add(1);
        }
    }

    private static final class TestIncomingCriticalPassive implements com.example.dueltower.engine.core.effect.passive.PassiveEffect {
        static final String ID = "TEST_INCOMING_CRITICAL_PASSIVE";

        @Override
        public String id() {
            return ID;
        }

        static PassiveDefinition definition() {
            return new PassiveDefinition(ID, "피격 치명 증폭 패시브", 10, "받는 치명 배율을 증가시킨다.");
        }

        @Override
        public Rational onIncomingCriticalAmountMultiplier(com.example.dueltower.engine.core.effect.passive.PassiveRuntime rt,
                                                      TargetRef source,
                                                      TargetRef target,
                                                      String kind,
                                                      Rational currentMultiplier) {
            return currentMultiplier.add(1);
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

            int finalAmount = damage;
            Rational multiplier = criticalAmountMultiplier(ec, src, target, "damage");
            if (multiplier.compareTo(Rational.ONE) > 0 && isCritical(ec, src, target, "damage")) {
                finalAmount = multiplyAndRound(finalAmount, multiplier);
                ec.out().add(new GameEvent.LogAppended(ec.actor().value() + " critical! damage x" + formatMultiplier(multiplier)));
            }

            DamageOps.apply(ec.state(), ec.ctx(), ec.out(), src, id, target, finalAmount,
                    KeywordOps.damageFlags(ec.state(), ec.ctx(), src, ec.cardId(), target));
        }


        private static String formatMultiplier(Rational multiplier) {
            if (multiplier.getDenominator() == 1L) {
                return Long.toString(multiplier.getNumerator());
            }
            return multiplier.getNumerator() + "/" + multiplier.getDenominator();
        }

        static int multiplyAndRound(int amount, Rational multiplier) {
            java.math.BigInteger numerator = java.math.BigInteger.valueOf(amount).multiply(java.math.BigInteger.valueOf(multiplier.getNumerator()));
            java.math.BigInteger denominator = java.math.BigInteger.valueOf(multiplier.getDenominator());
            java.math.BigInteger[] divRem = numerator.divideAndRemainder(denominator);
            java.math.BigInteger quotient = divRem[0];
            java.math.BigInteger absTwiceRem = divRem[1].abs().shiftLeft(1);
            if (absTwiceRem.compareTo(denominator) >= 0) {
                quotient = quotient.add(java.math.BigInteger.valueOf(numerator.signum() >= 0 ? 1L : -1L));
            }
            return quotient.intValueExact();
        }


        private Rational criticalAmountMultiplier(EffectContext ec, TargetRef source, TargetRef target, String kind) {
            Rational multiplier = KeywordOps.criticalAmountMultiplier(ec.state(), ec.ctx(), source, ec.cardId(), target, kind);

            multiplier = PassiveOps.incomingCriticalAmountMultiplier(ec.state(), ec.ctx(), ec.out(), source, target, kind, multiplier, ec.actor().value());
            multiplier = applyStatusIncomingCriticalAmountMultiplier(ec, source, target, kind, multiplier);

            multiplier = applyStatusCriticalAmountMultiplier(ec, source, target, kind, multiplier);
            return PassiveOps.criticalAmountMultiplier(ec.state(), ec.ctx(), ec.out(), source, target, kind, multiplier, ec.actor().value());
        }

        private boolean isCritical(EffectContext ec, TargetRef source, TargetRef target, String kind) {
            int chance = KeywordOps.criticalChancePercent(ec.state(), ec.ctx(), source, ec.cardId(), target, kind);
            chance = PassiveOps.criticalChancePercent(ec.state(), ec.ctx(), ec.out(), source, target, kind, chance, ec.actor().value());
            chance = applyStatusCriticalChancePercent(ec, source, target, kind, chance);

            chance = PassiveOps.incomingCriticalChancePercent(ec.state(), ec.ctx(), ec.out(), source, target, kind, chance, ec.actor().value());
            chance = applyStatusIncomingCriticalChancePercent(ec, source, target, kind, chance);
            if (chance == 0) return false;

            long mix = ec.state().seed();
            mix ^= (ec.state().version() * 0x9E3779B97F4A7C15L);
            mix ^= ((long) ec.out().size() << 32);
            if (ec.cardId() != null) mix ^= ec.cardId().value().hashCode();
            mix ^= target.toString().hashCode();
            mix ^= kind.hashCode();

            Random rnd = new Random(mix);
            int roll = rnd.nextInt(100) + 1;
            return roll <= chance;
        }

        private int applyStatusCriticalChancePercent(EffectContext ec, TargetRef source, TargetRef target, String kind, int baseChance) {
            StatusRuntime rt = new StatusRuntime(ec.state(), ec.ctx(), ec.out(), ec.actor().value());
            int cur = baseChance;
            for (HookEntry it : collectStatusEntries(ec, rt, source)) {
                if (!ec.ctx().hasStatusEffect(it.statusId())) continue;
                int stacks = rt.stacks(it.owner(), it.statusId());
                if (stacks <= 0) continue;
                cur = ec.ctx().statusEffect(it.statusId()).onCriticalChancePercent(rt, it.owner(), source, target, kind, cur);
            }
            return Math.max(0, Math.min(100, cur));
        }

        private Rational applyStatusCriticalAmountMultiplier(EffectContext ec, TargetRef source, TargetRef target, String kind, Rational baseMultiplier) {
            StatusRuntime rt = new StatusRuntime(ec.state(), ec.ctx(), ec.out(), ec.actor().value());
            Rational cur = Rational.max(Rational.ONE, baseMultiplier);
            for (HookEntry it : collectStatusEntries(ec, rt, source)) {
                if (!ec.ctx().hasStatusEffect(it.statusId())) continue;
                int stacks = rt.stacks(it.owner(), it.statusId());
                if (stacks <= 0) continue;
                cur = ec.ctx().statusEffect(it.statusId()).onCriticalAmountMultiplier(rt, it.owner(), source, target, kind, cur);
            }
            return Rational.max(Rational.ONE, cur);
        }

        private int applyStatusIncomingCriticalChancePercent(EffectContext ec, TargetRef source, TargetRef target, String kind, int baseChance) {
            StatusRuntime rt = new StatusRuntime(ec.state(), ec.ctx(), ec.out(), ec.actor().value());
            int cur = baseChance;
            for (HookEntry it : collectStatusEntries(ec, rt, target)) {
                if (!ec.ctx().hasStatusEffect(it.statusId())) continue;
                int stacks = rt.stacks(it.owner(), it.statusId());
                if (stacks <= 0) continue;
                cur = ec.ctx().statusEffect(it.statusId()).onIncomingCriticalChancePercent(rt, it.owner(), source, target, kind, cur);
            }
            return Math.max(0, Math.min(100, cur));
        }

        private Rational applyStatusIncomingCriticalAmountMultiplier(EffectContext ec, TargetRef source, TargetRef target, String kind, Rational baseMultiplier) {
            StatusRuntime rt = new StatusRuntime(ec.state(), ec.ctx(), ec.out(), ec.actor().value());
            Rational cur = Rational.max(Rational.ONE, baseMultiplier);
            for (HookEntry it : collectStatusEntries(ec, rt, target)) {
                if (!ec.ctx().hasStatusEffect(it.statusId())) continue;
                int stacks = rt.stacks(it.owner(), it.statusId());
                if (stacks <= 0) continue;
                cur = ec.ctx().statusEffect(it.statusId()).onIncomingCriticalAmountMultiplier(rt, it.owner(), source, target, kind, cur);
            }
            return Rational.max(Rational.ONE, cur);
        }

        private record HookEntry(StatusOwnerRef owner, String statusId, int priority) {}

        private List<HookEntry> collectStatusEntries(EffectContext ec, StatusRuntime rt, TargetRef owner) {
            List<HookEntry> entries = new ArrayList<>();

            var ownerChar = StatusOwnerRef.of(owner);
            for (String k : rt.statusMap(ownerChar).keySet()) {
                entries.add(new HookEntry(ownerChar, k, ec.ctx().hasStatusDef(k) ? ec.ctx().statusDef(k).priority() : Integer.MAX_VALUE));
            }

            CombatState cs = ec.state().combat();
            if (cs != null) {
                var ownerFaction = StatusOwnerRef.of(CombatState.factionOf(owner));
                for (String k : rt.statusMap(ownerFaction).keySet()) {
                    entries.add(new HookEntry(ownerFaction, k, ec.ctx().hasStatusDef(k) ? ec.ctx().statusDef(k).priority() : Integer.MAX_VALUE));
                }
            }

            entries.sort(Comparator.comparingInt(HookEntry::priority));
            return entries;
        }
    }

    private static final class ScaleWithActorAttackCardEffect implements CardEffect {
        private final String id;

        private ScaleWithActorAttackCardEffect(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        CardDefinition definition() {
            return new CardDefinition(new CardDefId(id), id, CardType.SKILL, 1, Map.of(), Zone.GRAVE, false, id);
        }

        @Override
        public List<String> validate(EffectContext ec) {
            return new EffectOps(ec).validateTarget(Target.ENEMY_ONE);
        }

        @Override
        public void resolve(EffectContext ec) {
            new EffectOps(ec).damageWithActorAttack(Target.ENEMY_ONE);
        }
    }

    private static final class ScaleWithActorHealCardEffect implements CardEffect {
        private final String id;

        private ScaleWithActorHealCardEffect(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        CardDefinition definition() {
            return new CardDefinition(new CardDefId(id), id, CardType.SKILL, 1, Map.of(), Zone.GRAVE, false, id);
        }

        @Override
        public List<String> validate(EffectContext ec) {
            return new EffectOps(ec).validateTarget(Target.ALLY_ONE);
        }

        @Override
        public void resolve(EffectContext ec) {
            new EffectOps(ec).healWithActorHeal(Target.ALLY_ONE);
        }
    }

    private static final class SelfStatusCardEffect implements CardEffect {
        private final String id;

        private SelfStatusCardEffect(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        CardDefinition definition() {
            return new CardDefinition(new CardDefId(id), id, CardType.SKILL, 1, Map.of(), Zone.GRAVE, false, id);
        }

        @Override
        public void resolve(EffectContext ec) {
            new EffectOps(ec).addStatus(Target.SELF, S105_Weak.ID, 2);
        }
    }

    private static final class SelfFixedHealCardEffect implements CardEffect {
        private final String id;

        private SelfFixedHealCardEffect(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        CardDefinition definition() {
            return new CardDefinition(new CardDefId(id), id, CardType.SKILL, 1, Map.of(), Zone.GRAVE, false, id);
        }

        @Override
        public void resolve(EffectContext ec) {
            new EffectOps(ec).heal(Target.SELF, 3);
        }
    }

    private static final class TargetFixedValueCardEffect implements CardEffect {
        private final String id;
        private final Target target;
        private final boolean damage;
        private final int value;
        private final Map<String, Integer> keywords;

        private TargetFixedValueCardEffect(String id, Target target, boolean damage, int value) {
            this(id, target, damage, value, Map.of());
        }

        private TargetFixedValueCardEffect(String id, Target target, boolean damage, int value, Map<String, Integer> keywords) {
            this.id = id;
            this.target = target;
            this.damage = damage;
            this.value = value;
            this.keywords = keywords;
        }

        @Override
        public String id() {
            return id;
        }

        CardDefinition definition() {
            return new CardDefinition(new CardDefId(id), id, CardType.SKILL, 1, keywords, Zone.GRAVE, false, id);
        }

        @Override
        public List<String> validate(EffectContext ec) {
            return new EffectOps(ec).validateTarget(target);
        }

        @Override
        public void resolve(EffectContext ec) {
            EffectOps ops = new EffectOps(ec);
            if (damage) {
                ops.damage(target, value);
                return;
            }
            ops.heal(target, value);
        }
    }
}
