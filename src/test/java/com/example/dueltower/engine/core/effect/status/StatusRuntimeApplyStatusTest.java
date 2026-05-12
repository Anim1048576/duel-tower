package com.example.dueltower.engine.core.effect.status;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.EffectOps;
import com.example.dueltower.engine.core.effect.passive.PassiveEffect;
import com.example.dueltower.engine.core.effect.passive.PassiveRuntime;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StatusRuntimeApplyStatusTest {
    private static final String TEST_STATUS = "TEST_STATUS";
    private static final Ids.PlayerId PLAYER_ID = new Ids.PlayerId("p1");

    @Test
    void effectOpsCardStatusKeepsExistingStackAmount() {
        Ids.CardDefId cardDefId = new Ids.CardDefId("C-STATUS");
        Ids.CardInstId cardId = new Ids.CardInstId(UUID.randomUUID());
        GameState state = stateWithPlayer();
        state.cardInstances().put(cardId, new CardInstance(cardId, cardDefId, PLAYER_ID, Zone.HAND));

        EngineContext ctx = context(
                Map.of(cardDefId, new CardDefinition(
                        cardDefId,
                        "Status Card",
                        CardType.SKILL,
                        1,
                        Map.of(),
                        Zone.GRAVE,
                        false,
                        "apply status"
                )),
                Map.of(),
                Map.of()
        );

        EffectContext ec = new EffectContext(
                state,
                ctx,
                PLAYER_ID,
                cardId,
                TargetSelection.empty(),
                new ArrayList<>()
        );

        new EffectOps(ec).addStatus(Target.SELF, TEST_STATUS, 3);

        assertEquals(3, state.player(PLAYER_ID).status(TEST_STATUS));
    }

    @Test
    void applyStatusReturnsResultForZeroToThree() {
        GameState state = stateWithPlayer();
        StatusApplyResult result = apply(state, 3);

        assertEquals(0, result.before());
        assertEquals(3, result.requestedAmount());
        assertEquals(3, result.modifiedAmount());
        assertEquals(3, result.after());
        assertEquals(3, result.actualAppliedAmount());
        assertTrue(result.changed());
    }

    @Test
    void applyStatusActualAppliedAmountUsesIncreaseOnly() {
        GameState state = stateWithPlayer();
        state.player(PLAYER_ID).statusSet(TEST_STATUS, 2);

        StatusApplyResult result = apply(state, 3);

        assertEquals(2, result.before());
        assertEquals(5, result.after());
        assertEquals(3, result.actualAppliedAmount());
    }

    @Test
    void applyStatusTreatsRemovalAsZeroActualAppliedAmount() {
        GameState state = stateWithPlayer();
        state.player(PLAYER_ID).statusSet(TEST_STATUS, 5);

        StatusApplyResult result = apply(state, -3);

        assertEquals(5, result.before());
        assertEquals(2, result.after());
        assertEquals(0, result.actualAppliedAmount());
        assertTrue(result.changed());
    }

    @Test
    void passiveHooksCanModifyAmountAndObserveActualAppliedAmount() {
        TestStatusPassive passive = new TestStatusPassive();
        GameState state = stateWithPlayer();
        state.player(PLAYER_ID).addPassiveId(TestStatusPassive.ID);
        EngineContext ctx = context(
                Map.of(),
                Map.of(TestStatusPassive.ID, TestStatusPassive.definition()),
                Map.of(TestStatusPassive.ID, passive)
        );

        StatusRuntime rt = new StatusRuntime(state, ctx, new ArrayList<>(), "test");
        StatusApplyResult result = rt.applyStatus(contextFor(state, ctx, 3));

        assertEquals(5, state.player(PLAYER_ID).status(TEST_STATUS));
        assertEquals(3, passive.beforeAmount);
        assertSame(result, passive.afterResult);
        assertEquals(5, passive.afterActualAppliedAmount);
    }

    private static StatusApplyResult apply(GameState state, int amount) {
        EngineContext ctx = context(Map.of(), Map.of(), Map.of());
        StatusRuntime rt = new StatusRuntime(state, ctx, new ArrayList<>(), "test");
        return rt.applyStatus(contextFor(state, ctx, amount));
    }

    private static StatusApplyContext contextFor(GameState state, EngineContext ctx, int amount) {
        return new StatusApplyContext(
                state,
                ctx,
                TargetRef.ofPlayer(PLAYER_ID),
                StatusOwnerRef.of(TargetRef.ofPlayer(PLAYER_ID)),
                TEST_STATUS,
                amount,
                null,
                null,
                "test",
                StatusApplySourceKind.SYSTEM
        );
    }

    private static GameState stateWithPlayer() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 1L);
        state.players().put(PLAYER_ID, new PlayerState(PLAYER_ID));
        return state;
    }

    private static EngineContext context(
            Map<Ids.CardDefId, CardDefinition> cardDefs,
            Map<String, PassiveDefinition> passiveDefs,
            Map<String, PassiveEffect> passiveEffects
    ) {
        StatusDefinition statusDefinition = new StatusDefinition(
                TEST_STATUS,
                "테스트 상태",
                StatusKind.BUFF,
                StatusScope.CHARACTER,
                Set.of(),
                10,
                false,
                "테스트용 상태"
        );
        return new EngineContext(
                cardDefs,
                Map.of(),
                Map.of(TEST_STATUS, statusDefinition),
                Map.of(),
                Map.of(),
                Map.of(),
                passiveDefs,
                passiveEffects
        );
    }

    private static final class TestStatusPassive implements PassiveEffect {
        static final String ID = "P_STATUS_TEST";

        int beforeAmount;
        int afterActualAppliedAmount;
        StatusApplyResult afterResult;

        static PassiveDefinition definition() {
            return new PassiveDefinition(ID, "상태 테스트 패시브", 10, "상태 부여량을 보정한다.");
        }

        @Override
        public String id() {
            return ID;
        }

        @Override
        public int onBeforeApplyStatus(PassiveRuntime rt, StatusApplyContext apply, int currentAmount) {
            beforeAmount = currentAmount;
            return currentAmount + 2;
        }

        @Override
        public void onAfterApplyStatus(PassiveRuntime rt, StatusApplyContext apply, StatusApplyResult result) {
            afterResult = result;
            afterActualAppliedAmount = result.actualAppliedAmount();
        }
    }
}
