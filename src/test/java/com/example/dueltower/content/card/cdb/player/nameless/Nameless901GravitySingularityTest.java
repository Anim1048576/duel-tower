package com.example.dueltower.content.card.cdb.player.nameless;

import com.example.dueltower.content.card.cdb.player.tig.Tig901_EX;
import com.example.dueltower.content.keyword.kdb.K007_ClearMind;
import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.content.passive.pdb.player.nameless.Nameless001_Passive;
import com.example.dueltower.content.passive.pdb.player.nameless.Nameless002_Passive;
import com.example.dueltower.content.status.sdb.S005_Taunt;
import com.example.dueltower.content.status.sdb.player.nameless.Nameless201_Entropy;
import com.example.dueltower.content.status.sdb.player.nameless.Nameless202_EventHorizon;
import com.example.dueltower.content.status.sdb.player.nameless.Nameless203_EventHorizonUsed;
import com.example.dueltower.engine.command.UseExCommand;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.core.combat.CombatCleanupOps;
import com.example.dueltower.engine.core.combat.DamageOps;
import com.example.dueltower.engine.core.combat.HealOps;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.keyword.KeywordEffect;
import com.example.dueltower.engine.core.effect.passive.PassiveEffect;
import com.example.dueltower.engine.core.effect.status.StatusEffect;
import com.example.dueltower.engine.core.effect.status.StatusPhases;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardInstance;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.CombatPhase;
import com.example.dueltower.engine.model.CombatState;
import com.example.dueltower.engine.model.EnemyState;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.KeywordDefinition;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.StatusDefinition;
import com.example.dueltower.engine.model.StatusVisibility;
import com.example.dueltower.engine.model.TargetRef;
import com.example.dueltower.engine.model.TargetSelection;
import com.example.dueltower.engine.model.Zone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Nameless901GravitySingularityTest {
    private static final Ids.PlayerId PLAYER_ID = new Ids.PlayerId("nameless");
    private static final Ids.PlayerId OTHER_ID = new Ids.PlayerId("other");
    private static final Ids.EnemyId ENEMY_ID = new Ids.EnemyId("enemy");
    private static final Ids.CardDefId FILLER_DEF_ID = new Ids.CardDefId("Nameless901_TestFiller");

    @Test
    @DisplayName("choiceId 없이 USE_EX 중력 특이점을 사용하면 validate 실패한다")
    void gravitySingularityRequiresChoiceId() {
        Fixture fx = new Fixture();
        fx.setNamelessEx();

        EngineResult result = fx.useNamelessEx(fx.enemyTarget(), null);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("choiceId is required"));
    }

    @Test
    @DisplayName("AUGMENT_1은 도발이 없으면 도발 7을 부여하고 입자 방출은 받지 않으며 입자 공명을 발동한다")
    void augment1AppliesTauntAndTriggersResonanceWithoutEmission() {
        Fixture fx = new Fixture();
        fx.player.passiveIds(List.of(Nameless001_Passive.ID, Nameless002_Passive.ID));
        fx.player.hp(10);
        fx.setNamelessEx();

        EngineResult result = fx.useNamelessEx(fx.enemyTarget(), Nameless901_EX.AUGMENT_1);

        assertTrue(result.accepted());
        assertEquals(7, fx.enemy.status(S005_Taunt.ID));
        assertEquals(12, fx.player.hp());
    }

    @Test
    @DisplayName("AUGMENT_1은 도발이 있으면 도발을 제거한다")
    void augment1RemovesExistingTaunt() {
        Fixture fx = new Fixture();
        fx.enemy.statusSet(S005_Taunt.ID, 3);
        fx.setNamelessEx();

        EngineResult result = fx.useNamelessEx(fx.enemyTarget(), Nameless901_EX.AUGMENT_1);

        assertTrue(result.accepted());
        assertEquals(0, fx.enemy.status(S005_Taunt.ID));
    }

    @Test
    @DisplayName("AUGMENT_2는 엔트로피가 없으면 엔트로피 4를 부여하고 입자 방출은 받지 않으며 입자 공명을 발동한다")
    void augment2AppliesEntropyAndTriggersResonanceWithoutEmission() {
        Fixture fx = new Fixture();
        fx.player.passiveIds(List.of(Nameless001_Passive.ID, Nameless002_Passive.ID));
        fx.player.hp(10);
        fx.setNamelessEx();

        EngineResult result = fx.useNamelessEx(fx.enemyTarget(), Nameless901_EX.AUGMENT_2);

        assertTrue(result.accepted());
        assertEquals(4, fx.enemy.status(Nameless201_Entropy.ID));
        assertEquals(11, fx.player.hp());
    }

    @Test
    @DisplayName("AUGMENT_2는 엔트로피가 있으면 엔트로피를 제거한다")
    void augment2RemovesExistingEntropy() {
        Fixture fx = new Fixture();
        fx.enemy.statusSet(Nameless201_Entropy.ID, 4);
        fx.setNamelessEx();

        EngineResult result = fx.useNamelessEx(fx.enemyTarget(), Nameless901_EX.AUGMENT_2);

        assertTrue(result.accepted());
        assertEquals(0, fx.enemy.status(Nameless201_Entropy.ID));
    }

    @Test
    @DisplayName("AUGMENT_3은 자기 자신 대상에게만 허용된다")
    void augment3AllowsSelfTarget() {
        Fixture fx = new Fixture();
        fx.setNamelessEx();

        EngineResult result = fx.useNamelessEx(fx.selfTarget(), Nameless901_EX.AUGMENT_3);

        assertTrue(result.accepted());
    }

    @Test
    @DisplayName("AUGMENT_3은 타인을 대상으로 하면 validate 실패한다")
    void augment3RejectsOtherTarget() {
        Fixture fx = new Fixture();
        fx.setNamelessEx();

        EngineResult result = fx.useNamelessEx(fx.otherTarget(), Nameless901_EX.AUGMENT_3);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("AUGMENT_3 requires self target"));
    }

    @Test
    @DisplayName("AUGMENT_3은 사건의 지평선 1과 EVENT_HORIZON_USED 1을 부여하고 마커 부여로 입자 공명을 발동하지 않는다")
    void augment3AppliesEventHorizonAndUsedMarkerWithoutMarkerResonance() {
        Fixture fx = new Fixture();
        fx.player.passiveIds(List.of(Nameless001_Passive.ID));
        fx.player.hp(10);
        fx.setNamelessEx();

        EngineResult result = fx.useNamelessEx(fx.selfTarget(), Nameless901_EX.AUGMENT_3);

        assertTrue(result.accepted());
        assertEquals(1, fx.player.status(Nameless202_EventHorizon.ID));
        assertEquals(1, fx.player.status(Nameless203_EventHorizonUsed.ID));
        assertEquals(10, fx.player.hp());
    }

    @Test
    @DisplayName("EVENT_HORIZON_USED가 이미 있으면 AUGMENT_3은 validate 실패한다")
    void augment3RejectsWhenAlreadyUsed() {
        Fixture fx = new Fixture();
        fx.player.statusSet(Nameless203_EventHorizonUsed.ID, 1);
        fx.setNamelessEx();

        EngineResult result = fx.useNamelessEx(fx.selfTarget(), Nameless901_EX.AUGMENT_3);

        assertFalse(result.accepted());
        assertTrue(result.errors().contains("AUGMENT_3 already used"));
    }

    @Test
    @DisplayName("EVENT_HORIZON_USED는 전투 종료 정리 후에도 남는다")
    void eventHorizonUsedPersistsAfterCombatCleanup() {
        Fixture fx = new Fixture();
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);
        fx.player.statusSet(Nameless203_EventHorizonUsed.ID, 1);

        CombatCleanupOps.cleanupAfterCombatEnd(fx.state, fx.ctx);

        assertEquals(0, fx.player.status(Nameless202_EventHorizon.ID));
        assertEquals(1, fx.player.status(Nameless203_EventHorizonUsed.ID));
    }

    @Test
    @DisplayName("EVENT_HORIZON_USED는 구현용 상태이고 자체 효과를 갖지 않는다")
    void eventHorizonUsedIsImplementationStatusAndHasNoHooks() {
        Fixture fx = new Fixture();
        StatusDefinition def = fx.ctx.statusDef(Nameless203_EventHorizonUsed.ID);
        fx.player.statusSet(Nameless203_EventHorizonUsed.ID, 1);
        fx.player.hp(10);

        StatusPhases.turnStart(fx.state, fx.ctx, TargetRef.ofPlayer(PLAYER_ID), fx.events, "TEST");
        StatusPhases.turnEnd(fx.state, fx.ctx, TargetRef.ofPlayer(PLAYER_ID), fx.events, "TEST");
        DamageOps.apply(fx.state, fx.ctx, fx.events, TargetRef.ofEnemy(ENEMY_ID), "test", TargetRef.ofPlayer(PLAYER_ID), 3);
        HealOps.apply(fx.state, fx.ctx, fx.events, TargetRef.ofEnemy(ENEMY_ID), "test", TargetRef.ofPlayer(PLAYER_ID), 2);

        assertEquals(StatusVisibility.IMPLEMENTATION, def.visibility());
        assertFalse(def.publicVisible());
        assertEquals(9, fx.player.hp());
        assertEquals(1, fx.player.status(Nameless203_EventHorizonUsed.ID));
    }

    @Test
    @DisplayName("기존 EX 카드들은 choiceId 없이도 정상 동작한다")
    void existingExCardsStillWorkWithoutChoiceId() {
        Fixture fx = new Fixture();
        fx.setTigEx();
        fx.addDeckCard(FILLER_DEF_ID);

        EngineResult result = fx.useTigEx(fx.enemyTarget());

        assertTrue(result.accepted());
        assertEquals(26, fx.enemy.hp());
    }

    private static final class Fixture {
        final Nameless901_EX gravity = new Nameless901_EX();
        final Tig901_EX tigEx = new Tig901_EX();
        final Nameless201_Entropy entropy = new Nameless201_Entropy();
        final Nameless202_EventHorizon eventHorizon = new Nameless202_EventHorizon();
        final Nameless203_EventHorizonUsed eventHorizonUsed = new Nameless203_EventHorizonUsed();
        final Nameless001_Passive particleResonance = new Nameless001_Passive();
        final Nameless002_Passive particleEmission = new Nameless002_Passive();
        final GameEngine engine = new GameEngine();
        final GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 901L);
        final PlayerState player = new PlayerState(PLAYER_ID);
        final PlayerState other = new PlayerState(OTHER_ID);
        final EnemyState enemy = new EnemyState(ENEMY_ID, 30);
        final List<GameEvent> events = new ArrayList<>();
        final EngineContext ctx;

        Fixture() {
            state.players().put(PLAYER_ID, player);
            state.players().put(OTHER_ID, other);
            state.enemies().put(ENEMY_ID, enemy);
            startCombat();

            KeywordBlueprint clearMind = new K007_ClearMind();
            ctx = new EngineContext(
                    Map.of(
                            new Ids.CardDefId(gravity.id()), gravity.definition(),
                            new Ids.CardDefId(tigEx.id()), tigEx.definition(),
                            FILLER_DEF_ID, new CardDefinition(FILLER_DEF_ID, "테스트 카드", CardType.SKILL, 0, Map.of(), Zone.GRAVE, false, "")
                    ),
                    Map.<Ids.CardDefId, CardEffect>of(
                            new Ids.CardDefId(gravity.id()), gravity,
                            new Ids.CardDefId(tigEx.id()), tigEx
                    ),
                    Map.of(
                            S005_Taunt.ID, new S005_Taunt().definition(),
                            Nameless201_Entropy.ID, entropy.definition(),
                            Nameless202_EventHorizon.ID, eventHorizon.definition(),
                            Nameless203_EventHorizonUsed.ID, eventHorizonUsed.definition()
                    ),
                    Map.<String, StatusEffect>of(
                            S005_Taunt.ID, new S005_Taunt(),
                            Nameless201_Entropy.ID, entropy,
                            Nameless202_EventHorizon.ID, eventHorizon,
                            Nameless203_EventHorizonUsed.ID, eventHorizonUsed
                    ),
                    Map.of(clearMind.id(), clearMind.definition()),
                    Map.<String, KeywordEffect>of(clearMind.id(), clearMind),
                    Map.of(
                            Nameless001_Passive.ID, particleResonance.definition(),
                            Nameless002_Passive.ID, particleEmission.definition()
                    ),
                    Map.<String, PassiveEffect>of(
                            Nameless001_Passive.ID, particleResonance,
                            Nameless002_Passive.ID, particleEmission
                    )
            );
        }

        void startCombat() {
            CombatState combat = new CombatState();
            combat.turnOrder().add(TargetRef.ofPlayer(PLAYER_ID));
            combat.turnOrder().add(TargetRef.ofEnemy(ENEMY_ID));
            combat.currentTurnIndex(0);
            combat.phase(CombatPhase.MAIN);
            state.combat(combat);
        }

        void setNamelessEx() {
            player.exCard(addCard(new Ids.CardDefId(gravity.id()), Zone.EX));
        }

        void setTigEx() {
            player.exCard(addCard(new Ids.CardDefId(tigEx.id()), Zone.EX));
        }

        Ids.CardInstId addDeckCard(Ids.CardDefId defId) {
            return addCard(defId, Zone.DECK);
        }

        Ids.CardInstId addCard(Ids.CardDefId defId, Zone zone) {
            Ids.CardInstId cardId = new Ids.CardInstId(UUID.randomUUID());
            state.cardInstances().put(cardId, new CardInstance(cardId, defId, PLAYER_ID, zone));
            switch (zone) {
                case EX -> player.exCard(cardId);
                case DECK -> player.deck().addLast(cardId);
                case HAND -> player.hand().add(cardId);
                case GRAVE -> player.grave().add(cardId);
                case FIELD -> player.field().add(cardId);
                case EXCLUDED -> player.excluded().add(cardId);
            }
            return cardId;
        }

        TargetSelection selfTarget() {
            return new TargetSelection(List.of(TargetRef.ofPlayer(PLAYER_ID)));
        }

        TargetSelection otherTarget() {
            return new TargetSelection(List.of(TargetRef.ofPlayer(OTHER_ID)));
        }

        TargetSelection enemyTarget() {
            return new TargetSelection(List.of(TargetRef.ofEnemy(ENEMY_ID)));
        }

        EngineResult useNamelessEx(TargetSelection selection, String choiceId) {
            return engine.process(
                    state,
                    ctx,
                    new UseExCommand(UUID.randomUUID(), state.version(), PLAYER_ID, selection, choiceId)
            );
        }

        EngineResult useTigEx(TargetSelection selection) {
            return engine.process(
                    state,
                    ctx,
                    new UseExCommand(UUID.randomUUID(), state.version(), PLAYER_ID, selection)
            );
        }
    }
}
