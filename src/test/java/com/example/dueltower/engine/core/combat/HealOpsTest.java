package com.example.dueltower.engine.core.combat;

import com.example.dueltower.content.keyword.kdb.K011_Critical;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.core.effect.status.StatusEffect;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HealOpsTest {

    @Test
    void criticalHealIsAppliedThroughHealPipeline() {
        PlayerId playerId = new PlayerId("P1");
        CardDefId defId = new CardDefId("C_HEAL");
        CardInstId cardId = new CardInstId("I_HEAL");

        GameState state = new GameState(new SessionId(UUID.randomUUID()), 1L);
        PlayerState player = new PlayerState(playerId);
        player.hp(5);
        state.players().put(playerId, player);
        state.cardInstances().put(cardId, new CardInstance(cardId, defId, playerId, Zone.HAND));

        EngineContext ctx = new EngineContext(
                Map.of(defId, new CardDefinition(defId, "critical-heal", CardType.SKILL, 0, Map.of(K011_Critical.ID, 10), Zone.GRAVE, false, "")),
                Map.of()
        );

        EffectOps ops = new EffectOps(new EffectContext(
                state,
                ctx,
                playerId,
                cardId,
                new TargetSelection(List.of(TargetRef.ofPlayer(playerId))),
                new ArrayList<>()
        ));

        ops.heal(Target.SELF, 3);
        assertEquals(11, player.hp());
    }

    @Test
    void factionIncomingHealEffectIsApplied() {
        PlayerId playerId = new PlayerId("P1");

        GameState state = new GameState(new SessionId(UUID.randomUUID()), 7L);
        PlayerState player = new PlayerState(playerId);
        player.hp(4);
        state.players().put(playerId, player);

        CombatState combat = new CombatState();
        combat.factionStatusValues(CombatState.FactionId.PLAYERS).put("AURA_HEAL_UP", 1);
        state.combat(combat);

        String healUp = "AURA_HEAL_UP";
        EngineContext ctx = new EngineContext(
                Map.of(),
                Map.of(),
                Map.of(healUp, new StatusDefinition(healUp, "heal-up", StatusKind.BUFF, StatusScope.FACTION, Set.of(), 1, true, "")),
                Map.of(healUp, new StatusEffect() {
                    @Override
                    public String id() {
                        return healUp;
                    }

                    @Override
                    public int onIncomingHeal(com.example.dueltower.engine.core.effect.status.StatusRuntime rt, StatusOwnerRef owner, TargetRef source, TargetRef target, int amount) {
                        return amount + 2;
                    }
                })
        );

        HealOps.apply(state, ctx, new ArrayList<>(), "test", TargetRef.ofPlayer(playerId), 3);
        assertEquals(9, player.hp());
    }

    @Test
    void healingCanBeBlockedOrTransformedByStatuses() {
        PlayerId playerId = new PlayerId("P1");

        GameState state = new GameState(new SessionId(UUID.randomUUID()), 9L);
        PlayerState player = new PlayerState(playerId);
        player.hp(3);
        state.players().put(playerId, player);

        String transform = "HALF_HEAL";
        String block = "BLOCK_HEAL";

        EngineContext ctx = new EngineContext(
                Map.of(),
                Map.of(),
                Map.of(
                        transform, new StatusDefinition(transform, "half", StatusKind.DEBUFF, StatusScope.CHARACTER, Set.of(), 1, true, ""),
                        block, new StatusDefinition(block, "block", StatusKind.DEBUFF, StatusScope.CHARACTER, Set.of(), 2, true, "")
                ),
                Map.of(
                        transform, new StatusEffect() {
                            @Override
                            public String id() {
                                return transform;
                            }

                            @Override
                            public int onIncomingHeal(com.example.dueltower.engine.core.effect.status.StatusRuntime rt, StatusOwnerRef owner, TargetRef source, TargetRef target, int amount) {
                                return amount / 2;
                            }
                        },
                        block, new StatusEffect() {
                            @Override
                            public String id() {
                                return block;
                            }

                            @Override
                            public int onIncomingHeal(com.example.dueltower.engine.core.effect.status.StatusRuntime rt, StatusOwnerRef owner, TargetRef source, TargetRef target, int amount) {
                                return 0;
                            }
                        }
                )
        );

        player.statusAdd(transform, 1);
        HealOps.apply(state, ctx, new ArrayList<>(), "test", TargetRef.ofPlayer(playerId), 6);
        assertEquals(6, player.hp());

        player.statusAdd(block, 1);
        HealOps.apply(state, ctx, new ArrayList<>(), "test", TargetRef.ofPlayer(playerId), 6);
        assertEquals(6, player.hp());
    }
}
