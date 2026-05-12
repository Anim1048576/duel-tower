package com.example.dueltower.content.passive.pdb.player.nameless;

import com.example.dueltower.content.status.sdb.S005_Taunt;
import com.example.dueltower.content.status.sdb.S106_Vulnerable;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.passive.PassiveEffect;
import com.example.dueltower.engine.core.effect.status.StatusEffect;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NamelessPassiveTest {
    private static final Ids.PlayerId NAMELESS_ID = new Ids.PlayerId("nameless");
    private static final Ids.EnemyId ENEMY_ID = new Ids.EnemyId("enemy");
    private static final Ids.CardDefId SKILL_DEF_ID = new Ids.CardDefId("Nameless_TestSkill");
    private static final Ids.CardDefId EX_DEF_ID = new Ids.CardDefId("Nameless_TestEx");

    @Test
    @DisplayName("입자 방출은 스킬 카드 상태 부여량을 1 늘리고 입자 공명은 실제 부여량 4로 1 회복한다")
    void skillStatusApplyGetsEmissionAndResonanceHeal() {
        Fixture fx = new Fixture();
        fx.player.hp(10);

        new EffectOps(fx.effectContext(fx.addCard(SKILL_DEF_ID))).addStatus(Target.SELF, S106_Vulnerable.ID, 3);

        assertEquals(4, fx.player.status(S106_Vulnerable.ID));
        assertEquals(11, fx.player.hp());
    }

    @Test
    @DisplayName("입자 방출은 EX 상태 부여에는 적용되지 않고 입자 공명은 실제 부여량 7로 2 회복한다")
    void exStatusApplySkipsEmissionButTriggersResonanceHeal() {
        Fixture fx = new Fixture();
        fx.player.hp(10);

        new EffectOps(fx.effectContext(fx.addCard(EX_DEF_ID))).addStatus(Target.SELF, S005_Taunt.ID, 7);

        assertEquals(7, fx.player.status(S005_Taunt.ID));
        assertEquals(12, fx.player.hp());
    }

    @Test
    @DisplayName("상태 실제 부여량이 2이면 입자 공명 회복량은 0이다")
    void resonanceRoundsDownToZeroForTwoAppliedStatus() {
        Fixture fx = new Fixture();
        fx.player.hp(10);

        new EffectOps(fx.effectContext(fx.addCard(EX_DEF_ID))).addStatus(Target.SELF, S005_Taunt.ID, 2);

        assertEquals(2, fx.player.status(S005_Taunt.ID));
        assertEquals(10, fx.player.hp());
    }

    @Test
    @DisplayName("직접 피해와 직접 회복만 하는 카드는 입자 공명을 발동하지 않는다")
    void directDamageAndHealDoNotTriggerResonance() {
        Fixture fx = new Fixture();
        Ids.CardInstId skillCardId = fx.addCard(SKILL_DEF_ID);
        fx.player.hp(10);

        new EffectOps(fx.effectContext(skillCardId)).heal(Target.SELF, 2);
        assertEquals(12, fx.player.hp());
        assertFalse(fx.events.stream().anyMatch(NamelessPassiveTest::isParticleResonanceLog));

        int eventCountAfterHeal = fx.events.size();
        new EffectOps(fx.effectContext(skillCardId, new TargetSelection(List.of(TargetRef.ofEnemy(ENEMY_ID)))))
                .damage(Target.ENEMY_ONE, 3);

        assertEquals(12, fx.player.hp());
        assertFalse(fx.events.subList(eventCountAfterHeal, fx.events.size()).stream()
                .anyMatch(NamelessPassiveTest::isParticleResonanceLog));
    }

    private static boolean isParticleResonanceLog(GameEvent event) {
        return event.toString().contains("입자 공명");
    }

    private static final class Fixture {
        final GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 101L);
        final PlayerState player = new PlayerState(NAMELESS_ID);
        final List<GameEvent> events = new ArrayList<>();
        final EngineContext ctx;

        Fixture() {
            player.passiveIds(List.of(Nameless001_Passive.ID, Nameless002_Passive.ID));
            state.players().put(NAMELESS_ID, player);
            state.enemies().put(ENEMY_ID, new EnemyState(ENEMY_ID, 30));
            ctx = new EngineContext(
                    Map.of(
                            SKILL_DEF_ID, new CardDefinition(SKILL_DEF_ID, "테스트 스킬", CardType.SKILL, 1, Map.of(), Zone.GRAVE, false, ""),
                            EX_DEF_ID, new CardDefinition(EX_DEF_ID, "테스트 EX", CardType.EX, 1, Map.of(), Zone.EX, false, "")
                    ),
                    Map.<Ids.CardDefId, CardEffect>of(),
                    Map.of(
                            S106_Vulnerable.ID, new S106_Vulnerable().definition(),
                            S005_Taunt.ID, new S005_Taunt().definition()
                    ),
                    Map.<String, StatusEffect>of(),
                    Map.of(),
                    Map.of(),
                    Map.of(
                            Nameless001_Passive.ID, new Nameless001_Passive().definition(),
                            Nameless002_Passive.ID, new Nameless002_Passive().definition()
                    ),
                    Map.<String, PassiveEffect>of(
                            Nameless001_Passive.ID, new Nameless001_Passive(),
                            Nameless002_Passive.ID, new Nameless002_Passive()
                    )
            );
        }

        Ids.CardInstId addCard(Ids.CardDefId defId) {
            Ids.CardInstId cardId = new Ids.CardInstId(UUID.randomUUID());
            state.cardInstances().put(cardId, new CardInstance(cardId, defId, NAMELESS_ID, Zone.HAND));
            return cardId;
        }

        EffectContext effectContext(Ids.CardInstId cardId) {
            return effectContext(cardId, TargetSelection.empty());
        }

        EffectContext effectContext(Ids.CardInstId cardId, TargetSelection selection) {
            return new EffectContext(state, ctx, NAMELESS_ID, cardId, selection, events);
        }
    }
}
