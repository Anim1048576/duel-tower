package com.example.dueltower.content.card.cdb.player.nameless;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.passive.pdb.player.nameless.Nameless001_Passive;
import com.example.dueltower.content.passive.pdb.player.nameless.Nameless002_Passive;
import com.example.dueltower.content.status.sdb.S001_Shield;
import com.example.dueltower.content.status.sdb.S002_Regeneration;
import com.example.dueltower.content.status.sdb.S003_Vigor;
import com.example.dueltower.content.status.sdb.S101_Pain;
import com.example.dueltower.content.status.sdb.S104_Destruction;
import com.example.dueltower.content.status.sdb.S106_Vulnerable;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.passive.PassiveEffect;
import com.example.dueltower.engine.core.effect.status.StatusEffect;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardInstance;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.CombatPhase;
import com.example.dueltower.engine.model.CombatState;
import com.example.dueltower.engine.model.EnemyState;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamelessSkillCardsTest {
    private static final Ids.PlayerId PLAYER_ID = new Ids.PlayerId("nameless");
    private static final Ids.EnemyId ENEMY_ID = new Ids.EnemyId("enemy");
    private static final Ids.CardDefId FILLER_DEF_ID = new Ids.CardDefId("Nameless_TestFiller");
    private static final Ids.CardDefId TOKEN_DEF_ID = new Ids.CardDefId("Nameless_TestToken");

    @Test
    @DisplayName("스페이스는 아군 대상에게 치유력만큼 회복한다")
    void spaceHealsAllyByHealPower() {
        Fixture fx = new Fixture();
        fx.player.hp(10);

        fx.resolve(fx.space, fx.playerTarget());

        assertEquals(12, fx.player.hp());
    }

    @Test
    @DisplayName("스페이스는 적 대상에게 공격력/2 + 입자 방출 1만큼 고통을 부여한다")
    void spaceAppliesPainToEnemyWithParticleEmission() {
        Fixture fx = new Fixture();
        fx.player.passiveIds(List.of(Nameless002_Passive.ID));

        fx.resolve(fx.space, fx.enemyTarget());

        assertEquals(2, fx.enemy.status(S101_Pain.ID));
    }

    @Test
    @DisplayName("유니버스는 아군 대상에게 치유력/2 + 입자 방출 1만큼 재생을 부여한다")
    void universeAppliesRegenerationToAllyWithParticleEmission() {
        Fixture fx = new Fixture();
        fx.player.passiveIds(List.of(Nameless002_Passive.ID));

        fx.resolve(fx.universe, fx.playerTarget());

        assertEquals(2, fx.player.status(S002_Regeneration.ID));
    }

    @Test
    @DisplayName("유니버스는 적 대상에게 공격력만큼 피해를 준다")
    void universeDamagesEnemyByAttackPower() {
        Fixture fx = new Fixture();

        fx.resolve(fx.universe, fx.enemyTarget());

        assertEquals(28, fx.enemy.hp());
    }

    @Test
    @DisplayName("코스모스는 아군 대상에게 치유력*2 + 입자 방출 1만큼 보호를 부여한다")
    void cosmosAppliesShieldToAllyWithParticleEmission() {
        Fixture fx = new Fixture();
        fx.player.passiveIds(List.of(Nameless002_Passive.ID));

        fx.resolve(fx.cosmos, fx.playerTarget());

        assertEquals(5, fx.player.status(S001_Shield.ID));
    }

    @Test
    @DisplayName("코스모스는 적 대상에게 공격력/2 + 입자 방출 1만큼 파괴를 부여한다")
    void cosmosAppliesDestructionToEnemyWithParticleEmission() {
        Fixture fx = new Fixture();
        fx.player.passiveIds(List.of(Nameless002_Passive.ID));

        fx.resolve(fx.cosmos, fx.enemyTarget());

        assertEquals(2, fx.enemy.status(S104_Destruction.ID));
    }

    @Test
    @DisplayName("카오스는 상태 부여가 없어 입자 공명을 발동하지 않는다")
    void chaosDoesNotTriggerParticleResonance() {
        Fixture fx = new Fixture();
        fx.player.passiveIds(List.of(Nameless001_Passive.ID, Nameless002_Passive.ID));
        fx.player.hp(10);

        fx.resolve(fx.chaos, fx.enemyTarget());

        assertEquals(10, fx.player.hp());
        assertFalse(fx.events.stream().anyMatch(event -> event.toString().contains("입자 공명")));
    }

    @Test
    @DisplayName("보이드는 공격력+치유력 피해 후 취약 4를 부여한다")
    void voidDamagesAndAppliesVulnerableWithParticleEmission() {
        Fixture fx = new Fixture();
        fx.player.passiveIds(List.of(Nameless002_Passive.ID));
        Ids.CardInstId discard = fx.addHandCard(FILLER_DEF_ID);

        fx.resolve(fx.voidCard, fx.enemyTarget(), List.of(discard));

        assertEquals(26, fx.enemy.hp());
        assertEquals(4, fx.enemy.status(S106_Vulnerable.ID));
    }

    @Test
    @DisplayName("공허는 공격력+치유력 회복 후 활력 4를 부여한다")
    void emptyHealsAndAppliesVigorWithParticleEmission() {
        Fixture fx = new Fixture();
        fx.player.passiveIds(List.of(Nameless002_Passive.ID));
        fx.player.hp(10);
        Ids.CardInstId discard = fx.addHandCard(FILLER_DEF_ID);

        fx.resolve(fx.empty, fx.playerTarget(), List.of(discard));

        assertEquals(14, fx.player.hp());
        assertEquals(4, fx.player.status(S003_Vigor.ID));
    }

    @Test
    @DisplayName("보이드와 공허는 discardIds로 지정한 패 카드 1장을 버린다")
    void voidAndEmptyDiscardOneSelectedHandCard() {
        Fixture fx = new Fixture();
        Ids.CardInstId voidDiscard = fx.addHandCard(FILLER_DEF_ID);
        Ids.CardInstId emptyDiscard = fx.addHandCard(FILLER_DEF_ID);

        fx.resolve(fx.voidCard, fx.enemyTarget(), List.of(voidDiscard));
        fx.resolve(fx.empty, fx.playerTarget(), List.of(emptyDiscard));

        assertTrue(fx.player.grave().contains(voidDiscard));
        assertTrue(fx.player.grave().contains(emptyDiscard));
        assertFalse(fx.player.hand().contains(voidDiscard));
        assertFalse(fx.player.hand().contains(emptyDiscard));
    }

    @Test
    @DisplayName("보이드와 공허는 토큰 카드도 버릴 수 있다")
    void voidAndEmptyCanDiscardTokenCards() {
        Fixture fx = new Fixture();
        Ids.CardInstId voidToken = fx.addHandCard(TOKEN_DEF_ID);
        Ids.CardInstId emptyToken = fx.addHandCard(TOKEN_DEF_ID);

        fx.resolve(fx.voidCard, fx.enemyTarget(), List.of(voidToken));
        fx.resolve(fx.empty, fx.playerTarget(), List.of(emptyToken));

        assertFalse(fx.player.hand().contains(voidToken));
        assertFalse(fx.player.hand().contains(emptyToken));
        assertNull(fx.state.card(voidToken));
        assertNull(fx.state.card(emptyToken));
    }

    @Test
    @DisplayName("보이드와 공허는 자기 자신을 discardIds로 지정하면 validate가 실패한다")
    void voidAndEmptyRejectDiscardingSourceCard() {
        Fixture fx = new Fixture();

        assertRejectsDiscardSelection(fx, fx.voidCard, List.of(fx.sourceCard(fx.voidCard)), "source card cannot be selected");
        assertRejectsDiscardSelection(fx, fx.empty, List.of(fx.sourceCard(fx.empty)), "source card cannot be selected");
    }

    @Test
    @DisplayName("보이드와 공허는 discardIds가 비어 있으면 validate가 실패한다")
    void voidAndEmptyRejectEmptyDiscardIds() {
        Fixture fx = new Fixture();

        assertRejectsDiscardSelection(fx, fx.voidCard, List.of(), "discardIds must contain exactly 1 card");
        assertRejectsDiscardSelection(fx, fx.empty, List.of(), "discardIds must contain exactly 1 card");
    }

    @Test
    @DisplayName("보이드와 공허는 discardIds가 2장 이상이면 validate가 실패한다")
    void voidAndEmptyRejectMultipleDiscardIds() {
        Fixture fx = new Fixture();
        Ids.CardInstId first = fx.addHandCard(FILLER_DEF_ID);
        Ids.CardInstId second = fx.addHandCard(FILLER_DEF_ID);

        assertRejectsDiscardSelection(fx, fx.voidCard, List.of(first, second), "discardIds must contain exactly 1 card");
        assertRejectsDiscardSelection(fx, fx.empty, List.of(first, second), "discardIds must contain exactly 1 card");
    }

    @Test
    @DisplayName("보이드와 공허는 손패에 없는 카드를 discardIds로 지정하면 validate가 실패한다")
    void voidAndEmptyRejectDiscardCardNotInHand() {
        Fixture fx = new Fixture();
        Ids.CardInstId missing = new Ids.CardInstId(UUID.randomUUID());

        assertRejectsDiscardSelection(fx, fx.voidCard, List.of(missing), "discard card not in hand");
        assertRejectsDiscardSelection(fx, fx.empty, List.of(missing), "discard card not in hand");
    }

    private static void assertRejectsDiscardSelection(
            Fixture fx,
            CardBlueprint card,
            List<Ids.CardInstId> discardIds,
            String expectedErrorPart
    ) {
        List<String> errors = card.validate(fx.effectContext(card, fx.enemyTarget(), discardIds));

        assertTrue(errors.stream().anyMatch(error -> error.contains(expectedErrorPart)),
                "expected error containing: " + expectedErrorPart + ", actual=" + errors);
    }

    private static final class Fixture {
        final Nameless001_Card space = new Nameless001_Card();
        final Nameless002_Card universe = new Nameless002_Card();
        final Nameless003_Card cosmos = new Nameless003_Card();
        final Nameless004_Card chaos = new Nameless004_Card();
        final Nameless005_Card voidCard = new Nameless005_Card();
        final Nameless006_Card empty = new Nameless006_Card();
        final Nameless001_Passive particleResonance = new Nameless001_Passive();
        final Nameless002_Passive particleEmission = new Nameless002_Passive();
        final GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 606L);
        final PlayerState player = new PlayerState(PLAYER_ID);
        final EnemyState enemy = new EnemyState(ENEMY_ID, 30);
        final List<GameEvent> events = new ArrayList<>();
        final Map<Ids.CardDefId, CardDefinition> definitions;
        final EngineContext ctx;

        Fixture() {
            player.passiveIds(List.of(Nameless002_Passive.ID));
            state.players().put(PLAYER_ID, player);
            state.enemies().put(ENEMY_ID, enemy);
            startCombat();

            definitions = Map.ofEntries(
                    Map.entry(new Ids.CardDefId(space.id()), space.definition()),
                    Map.entry(new Ids.CardDefId(universe.id()), universe.definition()),
                    Map.entry(new Ids.CardDefId(cosmos.id()), cosmos.definition()),
                    Map.entry(new Ids.CardDefId(chaos.id()), chaos.definition()),
                    Map.entry(new Ids.CardDefId(voidCard.id()), voidCard.definition()),
                    Map.entry(new Ids.CardDefId(empty.id()), empty.definition()),
                    Map.entry(FILLER_DEF_ID, new CardDefinition(FILLER_DEF_ID, "테스트 카드", CardType.SKILL, 0, Map.of(), Zone.GRAVE, false, "")),
                    Map.entry(TOKEN_DEF_ID, new CardDefinition(TOKEN_DEF_ID, "테스트 토큰", CardType.SKILL, 0, Map.of(), Zone.GRAVE, true, ""))
            );
            ctx = new EngineContext(
                    definitions,
                    Map.<Ids.CardDefId, CardEffect>of(),
                    Map.of(
                            S001_Shield.ID, new S001_Shield().definition(),
                            S002_Regeneration.ID, new S002_Regeneration().definition(),
                            S003_Vigor.ID, new S003_Vigor().definition(),
                            S101_Pain.ID, new S101_Pain().definition(),
                            S104_Destruction.ID, new S104_Destruction().definition(),
                            S106_Vulnerable.ID, new S106_Vulnerable().definition()
                    ),
                    Map.<String, StatusEffect>of(
                            S001_Shield.ID, new S001_Shield(),
                            S002_Regeneration.ID, new S002_Regeneration(),
                            S003_Vigor.ID, new S003_Vigor(),
                            S101_Pain.ID, new S101_Pain(),
                            S104_Destruction.ID, new S104_Destruction(),
                            S106_Vulnerable.ID, new S106_Vulnerable()
                    ),
                    Map.of(),
                    Map.of(),
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

        TargetSelection playerTarget() {
            return new TargetSelection(List.of(TargetRef.ofPlayer(PLAYER_ID)));
        }

        TargetSelection enemyTarget() {
            return new TargetSelection(List.of(TargetRef.ofEnemy(ENEMY_ID)));
        }

        void resolve(CardBlueprint card, TargetSelection selection) {
            resolve(card, selection, List.of());
        }

        void resolve(CardBlueprint card, TargetSelection selection, List<Ids.CardInstId> discardIds) {
            card.resolve(effectContext(card, selection, discardIds));
        }

        EffectContext effectContext(CardBlueprint card, TargetSelection selection, List<Ids.CardInstId> discardIds) {
            return new EffectContext(
                    state,
                    ctx,
                    PLAYER_ID,
                    sourceCard(card),
                    selection,
                    discardIds,
                    events
            );
        }

        Ids.CardInstId sourceCard(CardBlueprint card) {
            Ids.CardDefId defId = new Ids.CardDefId(card.id());
            for (Ids.CardInstId handCard : player.hand()) {
                CardInstance ci = state.card(handCard);
                if (ci != null && ci.defId().equals(defId)) {
                    return handCard;
                }
            }
            return addHandCard(defId);
        }

        Ids.CardInstId addHandCard(Ids.CardDefId defId) {
            assertNotNull(definitions.get(defId));
            Ids.CardInstId cardId = new Ids.CardInstId(UUID.randomUUID());
            state.cardInstances().put(cardId, new CardInstance(cardId, defId, PLAYER_ID, Zone.HAND));
            player.hand().add(cardId);
            return cardId;
        }
    }
}
