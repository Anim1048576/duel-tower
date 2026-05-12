package com.example.dueltower.content.status.sdb.player.nameless;

import com.example.dueltower.content.passive.pdb.player.nameless.Nameless001_Passive;
import com.example.dueltower.content.status.sdb.S106_Vulnerable;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.command.EndTurnCommand;
import com.example.dueltower.engine.command.CommandValidation;
import com.example.dueltower.engine.command.ResolveEventHorizonCommand;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.core.combat.DamageFlags;
import com.example.dueltower.engine.core.combat.DamageOps;
import com.example.dueltower.engine.core.combat.HealOps;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.passive.PassiveEffect;
import com.example.dueltower.engine.core.effect.status.StatusEffect;
import com.example.dueltower.engine.core.effect.status.StatusOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Nameless202EventHorizonTest {
    private static final Ids.PlayerId PLAYER_ID = new Ids.PlayerId("nameless");
    private static final Ids.EnemyId ENEMY_ID = new Ids.EnemyId("enemy");
    private static final Ids.CardDefId SKILL_DEF_ID = new Ids.CardDefId("EH_TestSkill");
    private static final Ids.CardDefId EX_DEF_ID = new Ids.CardDefId("EH_TestEx");

    @Test
    void eventHorizonDefinitionIsBuff() {
        assertEquals(StatusKind.BUFF, new Nameless202_EventHorizon().definition().kind());
    }

    @Test
    @DisplayName("사건의 지평선 상태에서는 일반 피해를 받지 않는다")
    void eventHorizonBlocksNormalDamage() {
        Fixture fx = new Fixture();
        fx.player.hp(10);
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);

        DamageOps.apply(fx.state, fx.ctx, fx.events, TargetRef.ofEnemy(ENEMY_ID), "normal", TargetRef.ofPlayer(PLAYER_ID), 5);

        assertEquals(10, fx.player.hp());
    }

    @Test
    @DisplayName("사건의 지평선 상태에서는 일반 회복을 받지 않는다")
    void eventHorizonBlocksNormalHeal() {
        Fixture fx = new Fixture();
        fx.player.hp(10);
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);

        HealOps.apply(fx.state, fx.ctx, fx.events, TargetRef.ofPlayer(PLAYER_ID), "normal", TargetRef.ofPlayer(PLAYER_ID), 5);

        assertEquals(10, fx.player.hp());
    }

    @Test
    @DisplayName("사건의 지평선 상태에서도 EVENT_HORIZON 출처 피해는 받는다")
    void eventHorizonAllowsOwnStatusDamage() {
        Fixture fx = new Fixture();
        fx.player.hp(10);
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);

        DamageOps.apply(
                fx.state,
                fx.ctx,
                fx.events,
                TargetRef.ofPlayer(PLAYER_ID),
                null,
                "다른 라벨",
                Nameless202_EventHorizon.ID,
                TargetRef.ofPlayer(PLAYER_ID),
                5,
                DamageFlags.NONE
        );

        assertEquals(5, fx.player.hp());
    }

    @Test
    @DisplayName("사건의 지평선 상태에서 스킬 카드를 이미 1장 사용한 턴에는 두 번째 스킬 카드 validate가 실패한다")
    void eventHorizonRejectsSecondSkillCard() {
        Fixture fx = new Fixture();
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);
        fx.player.cardsPlayedThisTurn(1);
        CardInstance skill = fx.card(SKILL_DEF_ID);
        List<String> errors = new ArrayList<>();

        StatusOps.validatePlayCard(fx.state, fx.ctx, TargetRef.ofPlayer(PLAYER_ID), skill, fx.ctx.def(SKILL_DEF_ID), errors);

        assertTrue(errors.contains("[사건의 지평선] 상태에서는 자신의 턴에 스킬 카드를 1장만 사용할 수 있습니다."));
    }

    @Test
    @DisplayName("사건의 지평선 상태에서도 EX validate는 실패하지 않는다")
    void eventHorizonDoesNotRejectEx() {
        Fixture fx = new Fixture();
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);
        fx.player.cardsPlayedThisTurn(1);
        CardInstance ex = fx.card(EX_DEF_ID);
        List<String> errors = new ArrayList<>();

        StatusOps.validateUseEx(fx.state, fx.ctx, TargetRef.ofPlayer(PLAYER_ID), ex, fx.ctx.def(EX_DEF_ID), errors);

        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("사건의 지평선 상태에서는 입자 공명 회복도 막힌다")
    void eventHorizonBlocksParticleResonanceHeal() {
        Fixture fx = new Fixture();
        fx.player.hp(10);
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);
        fx.player.passiveIds(List.of(Nameless001_Passive.ID));
        Ids.CardInstId cardId = fx.addCard(SKILL_DEF_ID);

        new EffectOps(new EffectContext(
                fx.state,
                fx.ctx,
                PLAYER_ID,
                cardId,
                TargetSelection.empty(),
                fx.events
        )).addStatus(Target.SELF, S106_Vulnerable.ID, 3);

        assertEquals(3, fx.player.status(S106_Vulnerable.ID));
        assertEquals(10, fx.player.hp());
    }

    @Test
    @DisplayName("사건의 지평선 상태에서 턴 종료 시 선택 pending이 생성된다")
    void eventHorizonCreatesPendingDecisionAtTurnEnd() {
        Fixture fx = new Fixture();
        fx.startCombatOnPlayerTurn();
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);

        EngineResult result = fx.engine.process(
                fx.state,
                fx.ctx,
                new EndTurnCommand(UUID.randomUUID(), fx.state.version(), PLAYER_ID)
        );

        assertTrue(result.accepted());
        assertTrue(fx.player.pendingDecision() instanceof PendingDecision.EventHorizonChoice decision
                && decision.choiceIds().equals(Nameless202_EventHorizon.CHOICE_IDS));
        assertTrue(result.events().stream().anyMatch(event -> event instanceof GameEvent.PendingDecisionSet set
                && set.playerId().equals(PLAYER_ID.value())
                && set.type().equals(Nameless202_EventHorizon.DECISION_TYPE)));
    }

    @Test
    @DisplayName("TAKE_DAMAGE 선택은 최대 체력 40% 사건의 지평선 피해를 받으며 피해 무시를 통과한다")
    void resolveEventHorizonTakeDamagePiercesEventHorizonPrevention() {
        Fixture fx = new Fixture();
        fx.player.hp(20);
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);
        fx.player.pendingDecision(new PendingDecision.EventHorizonChoice(
                "test",
                PLAYER_ID,
                Nameless202_EventHorizon.CHOICE_IDS
        ));

        EngineResult result = fx.engine.process(
                fx.state,
                fx.ctx,
                new ResolveEventHorizonCommand(
                        UUID.randomUUID(),
                        fx.state.version(),
                        PLAYER_ID,
                        Nameless202_EventHorizon.CHOICE_TAKE_DAMAGE
                )
        );

        assertTrue(result.accepted());
        assertEquals(12, fx.player.hp());
        assertEquals(1, fx.player.status(Nameless202_EventHorizon.ID));
        assertEquals(null, fx.player.pendingDecision());
    }

    @Test
    @DisplayName("REMOVE_STATUS 선택은 사건의 지평선을 제거한다")
    void resolveEventHorizonRemoveStatusClearsStatus() {
        Fixture fx = new Fixture();
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);
        fx.player.pendingDecision(new PendingDecision.EventHorizonChoice(
                "test",
                PLAYER_ID,
                Nameless202_EventHorizon.CHOICE_IDS
        ));

        EngineResult result = fx.engine.process(
                fx.state,
                fx.ctx,
                new ResolveEventHorizonCommand(
                        UUID.randomUUID(),
                        fx.state.version(),
                        PLAYER_ID,
                        Nameless202_EventHorizon.CHOICE_REMOVE_STATUS
                )
        );

        assertTrue(result.accepted());
        assertEquals(0, fx.player.status(Nameless202_EventHorizon.ID));
        assertEquals(null, fx.player.pendingDecision());
    }

    @Test
    @DisplayName("잘못된 사건의 지평선 choiceId는 validate 실패한다")
    void resolveEventHorizonRejectsInvalidChoiceId() {
        Fixture fx = new Fixture();
        fx.player.statusSet(Nameless202_EventHorizon.ID, 1);
        fx.player.pendingDecision(new PendingDecision.EventHorizonChoice(
                "test",
                PLAYER_ID,
                Nameless202_EventHorizon.CHOICE_IDS
        ));

        EngineResult result = fx.engine.process(
                fx.state,
                fx.ctx,
                new ResolveEventHorizonCommand(UUID.randomUUID(), fx.state.version(), PLAYER_ID, "NOPE")
        );

        assertTrue(result.errors().contains("invalid event horizon choice"));
    }

    @Test
    @DisplayName("pending 소유자가 아닌 플레이어는 사건의 지평선 선택을 해결할 수 없다")
    void resolveEventHorizonRejectsNonOwner() {
        Fixture fx = new Fixture();
        Ids.PlayerId otherId = new Ids.PlayerId("other");
        PlayerState other = new PlayerState(otherId);
        fx.state.players().put(otherId, other);
        other.pendingDecision(new PendingDecision.EventHorizonChoice(
                "test",
                PLAYER_ID,
                Nameless202_EventHorizon.CHOICE_IDS
        ));

        EngineResult result = fx.engine.process(
                fx.state,
                fx.ctx,
                new ResolveEventHorizonCommand(
                        UUID.randomUUID(),
                        fx.state.version(),
                        otherId,
                        Nameless202_EventHorizon.CHOICE_TAKE_DAMAGE
                )
        );

        assertTrue(result.errors().contains("event horizon pending owner mismatch"));
    }

    @Test
    @DisplayName("사건의 지평선 상태가 없으면 RESOLVE_EVENT_HORIZON은 실패한다")
    void resolveEventHorizonRejectsWhenStatusMissing() {
        Fixture fx = new Fixture();
        fx.player.pendingDecision(new PendingDecision.EventHorizonChoice(
                "test",
                PLAYER_ID,
                Nameless202_EventHorizon.CHOICE_IDS
        ));

        EngineResult result = fx.engine.process(
                fx.state,
                fx.ctx,
                new ResolveEventHorizonCommand(
                        UUID.randomUUID(),
                        fx.state.version(),
                        PLAYER_ID,
                        Nameless202_EventHorizon.CHOICE_TAKE_DAMAGE
                )
        );

        assertTrue(result.errors().contains("event horizon status is not active"));
    }

    @Test
    @DisplayName("사건의 지평선 pending이 열려 있으면 다음 액터의 일반 메인 행동도 막힌다")
    void eventHorizonPendingBlocksMainTurnActionsGlobally() {
        Fixture fx = new Fixture();
        Ids.PlayerId otherId = new Ids.PlayerId("other");
        fx.state.players().put(otherId, new PlayerState(otherId));
        CombatState combat = new CombatState();
        combat.turnOrder().add(TargetRef.ofPlayer(PLAYER_ID));
        combat.turnOrder().add(TargetRef.ofPlayer(otherId));
        combat.currentTurnIndex(1);
        combat.phase(CombatPhase.MAIN);
        fx.state.combat(combat);
        fx.player.pendingDecision(new PendingDecision.EventHorizonChoice(
                "test",
                PLAYER_ID,
                Nameless202_EventHorizon.CHOICE_IDS
        ));
        List<String> errors = new ArrayList<>();

        CommandValidation.validateMainTurn(fx.state, otherId, errors);

        assertTrue(errors.contains("pending decision exists"));
    }

    private static final class Fixture {
        final Nameless202_EventHorizon eventHorizon = new Nameless202_EventHorizon();
        final Nameless001_Passive particleResonance = new Nameless001_Passive();
        final GameEngine engine = new GameEngine();
        final GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 202L);
        final PlayerState player = new PlayerState(PLAYER_ID);
        final List<GameEvent> events = new ArrayList<>();
        final EngineContext ctx;

        Fixture() {
            state.players().put(PLAYER_ID, player);
            state.enemies().put(ENEMY_ID, new EnemyState(ENEMY_ID, 20));
            ctx = new EngineContext(
                    Map.of(
                            SKILL_DEF_ID, new CardDefinition(SKILL_DEF_ID, "테스트 스킬", CardType.SKILL, 0, Map.of(), Zone.GRAVE, false, ""),
                            EX_DEF_ID, new CardDefinition(EX_DEF_ID, "테스트 EX", CardType.EX, 0, Map.of(), Zone.EX, false, "")
                    ),
                    Map.<Ids.CardDefId, CardEffect>of(),
                    Map.of(
                            Nameless202_EventHorizon.ID, eventHorizon.definition(),
                            S106_Vulnerable.ID, new S106_Vulnerable().definition()
                    ),
                    Map.<String, StatusEffect>of(Nameless202_EventHorizon.ID, eventHorizon),
                    Map.of(),
                    Map.of(),
                    Map.of(Nameless001_Passive.ID, particleResonance.definition()),
                    Map.<String, PassiveEffect>of(Nameless001_Passive.ID, particleResonance)
            );
        }

        CardInstance card(Ids.CardDefId defId) {
            Ids.CardInstId cardId = addCard(defId);
            return state.card(cardId);
        }

        Ids.CardInstId addCard(Ids.CardDefId defId) {
            Ids.CardInstId cardId = new Ids.CardInstId(UUID.randomUUID());
            state.cardInstances().put(cardId, new CardInstance(cardId, defId, PLAYER_ID, Zone.HAND));
            return cardId;
        }

        void startCombatOnPlayerTurn() {
            CombatState combat = new CombatState();
            combat.turnOrder().add(TargetRef.ofPlayer(PLAYER_ID));
            combat.turnOrder().add(TargetRef.ofEnemy(ENEMY_ID));
            combat.currentTurnIndex(0);
            combat.phase(CombatPhase.MAIN);
            state.combat(combat);
        }
    }
}
