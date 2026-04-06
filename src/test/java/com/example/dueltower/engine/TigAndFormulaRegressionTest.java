package com.example.dueltower.engine;

import com.example.dueltower.content.card.cdb.C001_BasicAttack;
import com.example.dueltower.content.card.cdb.C002_BasicRecovery;
import com.example.dueltower.content.card.cdb.C004_BasicCurse;
import com.example.dueltower.content.card.cdb.player.tig.Tig001_Card;
import com.example.dueltower.content.card.cdb.player.tig.Tig005_Card;
import com.example.dueltower.content.card.cdb.player.tig.Tig006_Card;
import com.example.dueltower.content.card.cdb.player.tig.Tig901_EX;
import com.example.dueltower.content.keyword.kdb.K003_Installed;
import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.keyword.kdb.K006_Immovable;
import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.content.status.sdb.S101_Pain;
import com.example.dueltower.content.status.sdb.player.tig.Tig201_Status;
import com.example.dueltower.engine.command.PlayCardCommand;
import com.example.dueltower.engine.command.UseExCommand;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.keyword.KeywordEffect;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.EnemyId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TigAndFormulaRegressionTest {

    @Test
    void discardGatedTigCardRejectsWhenDiscardIdMissing() {
        TigFixture fx = new TigFixture();
        CardInstId tig005 = fx.addToHand("Tig005_Card");

        EngineResult result = fx.play(tig005, List.of(), List.of());

        assertFalse(result.accepted());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("discardIds must contain exactly 1 card")));
    }

    @Test
    void discardGatedTigCardExecutesPayloadWhenSelectedDiscardSucceeds() {
        TigFixture fx = new TigFixture();
        CardInstId tig005 = fx.addToHand("Tig005_Card");
        CardInstId discard1 = fx.addToHand("C001");
        CardInstId discard2 = fx.addToHand("C002");

        int hpBefore = fx.enemy1.hp();
        EngineResult result = fx.play(tig005, List.of(), List.of(discard2));

        assertTrue(result.accepted());
        assertTrue(hpBefore > fx.enemy1.hp(), "payload damage should apply after discard success");
        assertEquals(Zone.HAND, fx.state.card(discard1).zone());
        assertEquals(Zone.GRAVE, fx.state.card(discard2).zone());
    }

    @Test
    void discardGatedTigCardRejectsWhenSourceCardIsSelected() {
        TigFixture fx = new TigFixture();
        CardInstId tig005 = fx.addToHand("Tig005_Card");
        fx.addToHand("C001");

        EngineResult result = fx.play(tig005, List.of(), List.of(tig005));

        assertFalse(result.accepted());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("source card cannot be selected for discard")));
    }

    @Test
    void discardGatedTigCardRejectsWhenDiscardCardNotInHand() {
        TigFixture fx = new TigFixture();
        CardInstId tig005 = fx.addToHand("Tig005_Card");
        CardInstId notInHand = fx.addToGrave("C001");
        fx.addToHand("C002");

        EngineResult result = fx.play(tig005, List.of(), List.of(notInHand));

        assertFalse(result.accepted());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("discard card not in hand")));
    }

    @Test
    void discardGatedTigCardRejectsWhenSelectedCardCannotBeDiscardedByEffect() {
        TigFixture fx = new TigFixture();
        CardInstId tig005 = fx.addToHand("Tig005_Card");
        CardInstId immovable = fx.addToHand("IMMOVABLE_TEST");

        EngineResult result = fx.play(tig005, List.of(), List.of(immovable));

        assertFalse(result.accepted());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("부동 카드는 버릴 수 없습니다.")));
    }

    @Test
    void tig001Overcome3PlusDoesNotDestroyInstalledCardUnlessSelected() {
        TigFixture fx = new TigFixture();
        fx.player.statusSet(Tig201_Status.ID, 3);
        CardInstId tig001 = fx.addToHand("Tig001_Card");
        CardInstId installed = fx.addToField("INSTALLED_TEST");

        EngineResult result = fx.play(tig001, List.of(TargetRef.ofEnemy(fx.enemy1Id)), List.of(), List.of());

        assertTrue(result.accepted());
        assertEquals(Zone.FIELD, fx.state.card(installed).zone());
        assertTrue(fx.player.field().contains(installed));
    }

    @Test
    void tig001Overcome3PlusDestroysOnlySelectedInstalledCard() {
        TigFixture fx = new TigFixture();
        fx.player.statusSet(Tig201_Status.ID, 3);
        CardInstId tig001 = fx.addToHand("Tig001_Card");
        CardInstId installed1 = fx.addToField("INSTALLED_TEST");
        CardInstId installed2 = fx.addToField("INSTALLED_TEST");

        EngineResult result = fx.play(tig001, List.of(TargetRef.ofEnemy(fx.enemy1Id)), List.of(), List.of(installed1));

        assertTrue(result.accepted());
        assertEquals(Zone.GRAVE, fx.state.card(installed1).zone());
        assertEquals(Zone.FIELD, fx.state.card(installed2).zone());
    }

    @Test
    void tig001RejectsSelectedIdsWhenOvercomeBelow3() {
        TigFixture fx = new TigFixture();
        fx.player.statusSet(Tig201_Status.ID, 2);
        CardInstId tig001 = fx.addToHand("Tig001_Card");
        CardInstId installed = fx.addToField("INSTALLED_TEST");

        EngineResult result = fx.play(tig001, List.of(TargetRef.ofEnemy(fx.enemy1Id)), List.of(), List.of(installed));

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("selectedIds not allowed when overcome is below 3"));
    }

    @Test
    void tig006AllowsDiscardAndZeroInstalledSelections() {
        TigFixture fx = new TigFixture();
        CardInstId tig006 = fx.addToHand("Tig006_Card");
        CardInstId discard = fx.addToHand("C001");
        CardInstId installed = fx.addToField("INSTALLED_TEST");

        EngineResult result = fx.play(tig006, List.of(), List.of(discard), List.of());

        assertTrue(result.accepted());
        assertEquals(Zone.GRAVE, fx.state.card(discard).zone());
        assertEquals(Zone.FIELD, fx.state.card(installed).zone());
    }

    @Test
    void tig006DestroysOnlySelectedInstalledCardsUpToThree() {
        TigFixture fx = new TigFixture();
        CardInstId tig006 = fx.addToHand("Tig006_Card");
        CardInstId discard = fx.addToHand("C001");
        CardInstId i1 = fx.addToField("INSTALLED_TEST");
        CardInstId i2 = fx.addToField("INSTALLED_TEST");
        CardInstId i3 = fx.addToField("INSTALLED_TEST");
        CardInstId i4 = fx.addToField("INSTALLED_TEST");

        EngineResult result = fx.play(tig006, List.of(), List.of(discard), List.of(i1, i2, i3));

        assertTrue(result.accepted());
        assertEquals(Zone.GRAVE, fx.state.card(i1).zone());
        assertEquals(Zone.GRAVE, fx.state.card(i2).zone());
        assertEquals(Zone.GRAVE, fx.state.card(i3).zone());
        assertEquals(Zone.FIELD, fx.state.card(i4).zone());
    }

    @Test
    void tig006RejectsTooManyInstalledSelections() {
        TigFixture fx = new TigFixture();
        CardInstId tig006 = fx.addToHand("Tig006_Card");
        CardInstId discard = fx.addToHand("C001");
        CardInstId i1 = fx.addToField("INSTALLED_TEST");
        CardInstId i2 = fx.addToField("INSTALLED_TEST");
        CardInstId i3 = fx.addToField("INSTALLED_TEST");
        CardInstId i4 = fx.addToField("INSTALLED_TEST");

        EngineResult result = fx.play(tig006, List.of(), List.of(discard), List.of(i1, i2, i3, i4));

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("selectedIds must contain between 0 and 3 cards"));
    }

    @Test
    void tig006RejectsNonInstalledSelection() {
        TigFixture fx = new TigFixture();
        CardInstId tig006 = fx.addToHand("Tig006_Card");
        CardInstId discard = fx.addToHand("C001");
        CardInstId nonInstalled = fx.addToField("C001");

        EngineResult result = fx.play(tig006, List.of(), List.of(discard), List.of(nonInstalled));

        assertFalse(result.accepted());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("selected installed-destroy target is invalid")));
    }

    @Test
    void tig005CostReductionAppliesDuringValidationAndPayment() {
        TigFixture fx = new TigFixture();
        fx.player.statusSet(Tig201_Status.ID, 3);
        fx.player.ap(1);

        CardInstId tig005 = fx.addToHand("Tig005_Card");
        CardInstId discardable = fx.addToHand("C001");

        EngineResult result = fx.play(tig005, List.of(), List.of(discardable));

        assertTrue(result.accepted(), "overcome 3+ should reduce Tig005 cost from 2 to 1");
        assertEquals(0, fx.player.ap(), "actual payment should consume 1 AP");
    }

    @Test
    void tig005StillFailsAtZeroApEvenWithOvercome3Plus() {
        TigFixture fx = new TigFixture();
        fx.player.statusSet(Tig201_Status.ID, 3);
        fx.player.ap(0);

        CardInstId tig005 = fx.addToHand("Tig005_Card");
        CardInstId discardable = fx.addToHand("C001");

        EngineResult result = fx.play(tig005, List.of(), List.of(discardable));

        assertFalse(result.accepted());
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("not enough ap")));
    }

    @Test
    void tig901ExWithOneTargetDealsTwoHitsAndAddsBonusHitAtOvercome3Plus() {
        TigFixture base = new TigFixture();
        CardInstId exBase = base.addToEx("Tig901_EX");
        base.player.exCard(exBase);

        int oneHit = base.player.attackPower();
        int hpBeforeBase = base.enemy1.hp();
        EngineResult baseResult = base.useEx(List.of(TargetRef.ofEnemy(base.enemy1Id)));
        assertTrue(baseResult.accepted());
        assertEquals(hpBeforeBase - (2 * oneHit), base.enemy1.hp());

        TigFixture over3 = new TigFixture();
        CardInstId exOver = over3.addToEx("Tig901_EX");
        over3.player.exCard(exOver);
        over3.player.statusSet(Tig201_Status.ID, 3);

        int overHit = over3.player.attackPower() + 3;
        int hpBeforeOver = over3.enemy1.hp();
        EngineResult overResult = over3.useEx(List.of(TargetRef.ofEnemy(over3.enemy1Id)));
        assertTrue(overResult.accepted());
        assertEquals(hpBeforeOver - (3 * overHit), over3.enemy1.hp());
    }

    @Test
    void tig901ExWithTwoTargetsDealsOneHitEachAndAddsBonusHitAtOvercome3Plus() {
        TigFixture base = new TigFixture();
        CardInstId exBase = base.addToEx("Tig901_EX");
        base.player.exCard(exBase);

        int oneHit = base.player.attackPower();
        int enemy1Before = base.enemy1.hp();
        int enemy2Before = base.enemy2.hp();
        EngineResult baseResult = base.useEx(List.of(TargetRef.ofEnemy(base.enemy1Id), TargetRef.ofEnemy(base.enemy2Id)));
        assertTrue(baseResult.accepted());
        assertEquals(enemy1Before - oneHit, base.enemy1.hp());
        assertEquals(enemy2Before - oneHit, base.enemy2.hp());

        TigFixture over3 = new TigFixture();
        CardInstId exOver = over3.addToEx("Tig901_EX");
        over3.player.exCard(exOver);
        over3.player.statusSet(Tig201_Status.ID, 3);

        int overHit = over3.player.attackPower() + 3;
        int enemy1BeforeOver = over3.enemy1.hp();
        int enemy2BeforeOver = over3.enemy2.hp();
        EngineResult overResult = over3.useEx(List.of(TargetRef.ofEnemy(over3.enemy1Id), TargetRef.ofEnemy(over3.enemy2Id)));
        assertTrue(overResult.accepted());
        assertEquals(enemy1BeforeOver - (2 * overHit), over3.enemy1.hp());
        assertEquals(enemy2BeforeOver - (2 * overHit), over3.enemy2.hp());
    }

    @Test
    void tig901ExDrawHappensAfterDamageBranch() {
        TigFixture fx = new TigFixture();
        CardInstId ex = fx.addToEx("Tig901_EX");
        fx.player.exCard(ex);
        fx.player.deck().clear();
        CardInstId refillCard = fx.addToGrave("C001");

        int hpBefore = fx.enemy1.hp();
        EngineResult result = fx.useEx(List.of(TargetRef.ofEnemy(fx.enemy1Id)));

        assertTrue(result.accepted());
        assertTrue(fx.enemy1.hp() < hpBefore);
        assertTrue(fx.player.hand().contains(refillCard), "draw should still occur after damage branch");

        int firstDeckRefilled = firstEventIndex(result.events(), GameEvent.DeckRefilled.class);
        int lastDamageLog = lastDamageLogIndex(result.events());
        assertTrue(firstDeckRefilled >= 0, "draw path should emit deck refill event in this setup");
        assertTrue(lastDamageLog >= 0, "damage logs should exist before draw");
        assertTrue(lastDamageLog < firstDeckRefilled, "damage should resolve before draw/refill event");
    }

    @Test
    void formulaRegressionCardsStillUseActorStats() {
        TigFixture c001Fx = new TigFixture();
        c001Fx.player.body(8);
        c001Fx.player.skill(2);
        c001Fx.player.sense(4);
        CardInstId c001 = c001Fx.addToHand("C001");

        int c001ExpectedDamage = c001Fx.player.attackPower();
        int c001EnemyBefore = c001Fx.enemy1.hp();
        EngineResult c001Result = c001Fx.play(c001, List.of(TargetRef.ofEnemy(c001Fx.enemy1Id)));
        assertTrue(c001Result.accepted());
        assertEquals(c001EnemyBefore - c001ExpectedDamage, c001Fx.enemy1.hp());

        TigFixture c002Fx = new TigFixture();
        c002Fx.player.sense(9);
        c002Fx.player.skill(4);
        c002Fx.player.hp(10);
        CardInstId c002 = c002Fx.addToHand("C002");

        int c002ExpectedHeal = c002Fx.player.healPower();
        int c002Before = c002Fx.player.hp();
        EngineResult c002Result = c002Fx.play(c002, List.of(TargetRef.ofPlayer(c002Fx.playerId)));
        assertTrue(c002Result.accepted());
        assertEquals(Math.min(c002Before + c002ExpectedHeal, c002Fx.player.maxHp()), c002Fx.player.hp());

        TigFixture c004Fx = new TigFixture();
        c004Fx.player.body(7);
        c004Fx.player.skill(3);
        c004Fx.player.sense(2);
        CardInstId c004 = c004Fx.addToHand("C004");

        int c004ExpectedPain = c004Fx.player.attackPower();
        EngineResult c004Result = c004Fx.play(c004, List.of(TargetRef.ofEnemy(c004Fx.enemy1Id)));
        assertTrue(c004Result.accepted());
        assertEquals(c004ExpectedPain, c004Fx.enemy1.status(S101_Pain.ID));
    }

    private static int firstEventIndex(List<GameEvent> events, Class<? extends GameEvent> type) {
        for (int i = 0; i < events.size(); i++) {
            if (type.isInstance(events.get(i))) return i;
        }
        return -1;
    }

    private static int lastDamageLogIndex(List<GameEvent> events) {
        for (int i = events.size() - 1; i >= 0; i--) {
            GameEvent e = events.get(i);
            if (e instanceof GameEvent.LogAppended l && l.line().contains(" deals ")) return i;
        }
        return -1;
    }

    private static final class TigFixture {
        final GameState state = new GameState(new SessionId(UUID.randomUUID()), 13L);
        final GameEngine engine = new GameEngine();
        final PlayerId playerId = new PlayerId("P1");
        final EnemyId enemy1Id = new EnemyId("E1");
        final EnemyId enemy2Id = new EnemyId("E2");
        final PlayerState player = new PlayerState(playerId);
        final EnemyState enemy1 = new EnemyState(enemy1Id, 120);
        final EnemyState enemy2 = new EnemyState(enemy2Id, 120);
        final EngineContext ctx;

        TigFixture() {
            player.body(6);
            player.skill(4);
            player.sense(4);
            player.will(18);
            player.hp(player.maxHp());
            player.ap(player.maxAp());

            state.players().put(playerId, player);
            state.enemies().put(enemy1Id, enemy1);
            state.enemies().put(enemy2Id, enemy2);

            Map<CardDefId, CardDefinition> defs = new HashMap<>();
            Map<CardDefId, com.example.dueltower.engine.core.effect.card.CardEffect> effects = new HashMap<>();
            registerCard(defs, effects, new C001_BasicAttack());
            registerCard(defs, effects, new C002_BasicRecovery());
            registerCard(defs, effects, new C004_BasicCurse());
            registerCard(defs, effects, new Tig001_Card());
            registerCard(defs, effects, new Tig005_Card());
            registerCard(defs, effects, new Tig006_Card());
            registerCard(defs, effects, new Tig901_EX());
            registerCard(defs, effects, new ImmovableTestCard());
            registerCard(defs, effects, new InstalledTestCard());

            Map<String, StatusDefinition> statusDefs = new HashMap<>();
            Map<String, com.example.dueltower.engine.core.effect.status.StatusEffect> statusEffects = new HashMap<>();
            registerStatus(statusDefs, statusEffects, new S101_Pain());
            registerStatus(statusDefs, statusEffects, new Tig201_Status());
            Map<String, KeywordDefinition> keywordDefs = new HashMap<>();
            Map<String, KeywordEffect> keywordEffects = new HashMap<>();
            registerKeyword(keywordDefs, keywordEffects, new K003_Installed());
            registerKeyword(keywordDefs, keywordEffects, new K006_Immovable());

            this.ctx = new EngineContext(defs, effects, statusDefs, statusEffects, keywordDefs, keywordEffects);

            state.combat(new CombatState());
            CombatState cs = state.combat();
            cs.turnOrder().clear();
            cs.turnOrder().add(TargetRef.ofPlayer(playerId));
            cs.currentTurnIndex(0);
            cs.phase(CombatPhase.MAIN);
            cs.round(1);
        }

        EngineResult play(CardInstId cardId, List<TargetRef> targets) {
            return play(cardId, targets, List.of());
        }

        EngineResult play(CardInstId cardId, List<TargetRef> targets, List<CardInstId> discardIds) {
            return play(cardId, targets, discardIds, List.of());
        }

        EngineResult play(CardInstId cardId, List<TargetRef> targets, List<CardInstId> discardIds, List<CardInstId> selectedIds) {
            return engine.process(
                    state,
                    ctx,
                    new PlayCardCommand(UUID.randomUUID(), state.version(), playerId, cardId, new TargetSelection(targets), discardIds, selectedIds)
            );
        }

        EngineResult useEx(List<TargetRef> targets) {
            return engine.process(state, ctx, new UseExCommand(UUID.randomUUID(), state.version(), playerId, new TargetSelection(targets)));
        }

        CardInstId addToHand(String defId) {
            return addCard(defId, Zone.HAND);
        }

        CardInstId addToEx(String defId) {
            return addCard(defId, Zone.EX);
        }

        CardInstId addToGrave(String defId) {
            return addCard(defId, Zone.GRAVE);
        }

        CardInstId addToField(String defId) {
            return addCard(defId, Zone.FIELD);
        }

        private CardInstId addCard(String defId, Zone zone) {
            CardInstId id = Ids.newCardInstId();
            CardInstance ci = new CardInstance(id, new CardDefId(defId), playerId, zone);
            state.cardInstances().put(id, ci);
            switch (zone) {
                case HAND -> player.hand().add(id);
                case GRAVE -> player.grave().add(id);
                case EX -> player.exCard(id);
                case DECK -> player.deck().addLast(id);
                case FIELD -> player.field().add(id);
                case EXCLUDED -> player.excluded().add(id);
            }
            return id;
        }

        private static void registerCard(Map<CardDefId, CardDefinition> defs,
                                         Map<CardDefId, com.example.dueltower.engine.core.effect.card.CardEffect> effects,
                                         CardBlueprint bp) {
            defs.put(bp.definition().id(), bp.definition());
            effects.put(bp.definition().id(), bp);
        }

        private static void registerStatus(Map<String, StatusDefinition> defs,
                                           Map<String, com.example.dueltower.engine.core.effect.status.StatusEffect> effects,
                                           StatusBlueprint bp) {
            defs.put(bp.id(), bp.definition());
            effects.put(bp.id(), bp);
        }

        private static void registerKeyword(Map<String, KeywordDefinition> defs,
                                            Map<String, KeywordEffect> effects,
                                            KeywordBlueprint bp) {
            defs.put(bp.id(), bp.definition());
            effects.put(bp.id(), bp);
        }
    }

    private static final class ImmovableTestCard implements CardBlueprint {
        @Override
        public String id() {
            return "IMMOVABLE_TEST";
        }

        @Override
        public CardDefinition definition() {
            return new CardDefinition(
                    new CardDefId(id()),
                    "Immovable Test",
                    CardType.SKILL,
                    0,
                    Map.of(K006_Immovable.ID, 1),
                    Zone.GRAVE,
                    false,
                    "test"
            );
        }

        @Override
        public void resolve(EffectContext ec) {
            // no-op
        }
    }

    private static final class InstalledTestCard implements CardBlueprint {
        @Override
        public String id() {
            return "INSTALLED_TEST";
        }

        @Override
        public CardDefinition definition() {
            return new CardDefinition(
                    new CardDefId(id()),
                    "Installed Test",
                    CardType.SKILL,
                    0,
                    Map.of(K003_Installed.ID, 1),
                    Zone.GRAVE,
                    false,
                    "test"
            );
        }

        @Override
        public void resolve(EffectContext ec) {
            // no-op
        }
    }
}
