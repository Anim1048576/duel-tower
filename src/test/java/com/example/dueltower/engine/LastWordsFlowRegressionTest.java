package com.example.dueltower.engine;

import com.example.dueltower.content.card.model.OwnedCardModifier;
import com.example.dueltower.content.cardmodifier.cmdb.CM105_WeakenedDiscardOneSkill;
import com.example.dueltower.content.cardmodifier.cmdb.CardModifierIds;
import com.example.dueltower.content.keyword.kdb.K014_LastWords;
import com.example.dueltower.content.status.sdb.S103_Pressure;
import com.example.dueltower.engine.command.HandSwapCommand;
import com.example.dueltower.engine.command.PlayCardCommand;
import com.example.dueltower.engine.command.ResolveLastWordsCommand;
import com.example.dueltower.engine.command.UseExCommand;
import com.example.dueltower.engine.command.UseSummonActionCommand;
import com.example.dueltower.engine.core.EffectDiscardOps;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.core.LastWordsDecisionOps;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.LastWordsBatchCollector;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.keyword.KeywordOps;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardInstance;
import com.example.dueltower.engine.model.CardModifierDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.CombatPhase;
import com.example.dueltower.engine.model.CombatState;
import com.example.dueltower.engine.model.EnemyState;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.EnemyId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import com.example.dueltower.engine.model.PendingDecision;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.SummonState;
import com.example.dueltower.engine.model.TargetSelection;
import com.example.dueltower.engine.model.TargetRef;
import com.example.dueltower.engine.model.Zone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LastWordsFlowRegressionTest {

    @Test
    @DisplayName("하나의 effect resolve에서 유언 카드 2장을 effect-discard하면 LAST_WORDS pending이 열린다")
    void playCardOpensPendingWithTwoDiscardedLastWordsCandidates() {
        Fx fx = new Fx();
        fx.registerDiscardAllLastWordsSource("SOURCE", 0);
        fx.registerTrackingLastWordsSkill("LW_ONE", 1);
        fx.registerTrackingLastWordsSkill("LW_TWO", 1);

        CardInstId sourceId = fx.addHandCard("SOURCE");
        CardInstId firstId = fx.addHandCard("LW_ONE");
        CardInstId secondId = fx.addHandCard("LW_TWO");

        EngineResult result = fx.play(sourceId);

        assertTrue(result.accepted());
        assertInstanceOf(PendingDecision.LastWordsChoice.class, fx.player.pendingDecision());
        PendingDecision.LastWordsChoice decision = (PendingDecision.LastWordsChoice) fx.player.pendingDecision();
        assertEquals(List.of(firstId, secondId), decision.candidateIds());
        assertTrue(decision.skippable());
        assertTrue(result.events().stream().anyMatch(event -> event instanceof GameEvent.PendingDecisionSet set
                && "LAST_WORDS".equals(set.type())));

        List<String> selectTwoErrors = new ResolveLastWordsCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                List.of(firstId, secondId)
        ).validate(fx.state, fx.ctx);
        assertTrue(selectTwoErrors.stream().anyMatch(it -> it.contains("selectedIds must contain 0 or 1 card")));
    }

    @Test
    @DisplayName("AP 부족 후보는 pending 생성 시 제외되고 모두 부족하면 LAST_WORDS가 열리지 않는다")
    void pendingCreationFiltersOutUnpayableCandidates() {
        Fx fx = new Fx();
        fx.player.ap(1);
        fx.registerDiscardAllLastWordsSource("SOURCE", 0);
        fx.registerTrackingLastWordsSkill("EXPENSIVE_A", 2);
        fx.registerTrackingLastWordsSkill("EXPENSIVE_B", 2);

        CardInstId sourceId = fx.addHandCard("SOURCE");
        fx.addHandCard("EXPENSIVE_A");
        fx.addHandCard("EXPENSIVE_B");

        EngineResult result = fx.play(sourceId);

        assertTrue(result.accepted());
        assertNull(fx.player.pendingDecision());
        assertTrue(result.events().stream().noneMatch(event -> event instanceof GameEvent.PendingDecisionSet set
                && "LAST_WORDS".equals(set.type())));
        assertTrue(result.events().stream().anyMatch(event -> event instanceof GameEvent.LogAppended log
                && log.line().contains("no payable candidates")));
    }

    @Test
    @DisplayName("effect-discard가 아닌 HAND_SWAP discard는 유언 후보가 되지 않는다")
    void nonEffectDiscardDoesNotOpenLastWordsPending() {
        Fx fx = new Fx();
        fx.registerTrackingLastWordsSkill("LW_SWAP", 1);
        fx.registerPlainSkill("FILLER", 0);

        CardInstId discardId = fx.addHandCard("LW_SWAP");
        fx.addDeckCard("FILLER");

        EngineResult result = fx.process(new HandSwapCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                discardId
        ));

        assertTrue(result.accepted());
        assertNull(fx.player.pendingDecision());
        assertTrue(result.events().stream().noneMatch(event -> event instanceof GameEvent.PendingDecisionSet set
                && "LAST_WORDS".equals(set.type())));
    }

    @Test
    @DisplayName("같은 카드가 같은 타이밍에 중복 등록되어도 collector에는 1번만 남는다")
    void collectorDeduplicatesSameCardRegistration() {
        LastWordsBatchCollector collector = new LastWordsBatchCollector(UUID.randomUUID());
        CardInstId cardId = fixedId("00000000-0000-0000-0000-000000000123");

        collector.register(cardId);
        collector.register(cardId);

        assertEquals(List.of(cardId), collector.candidateIds());
    }

    @Test
    @DisplayName("이미 다른 pendingDecision이 있으면 LAST_WORDS를 열지 않는다")
    void openPendingSkipsWhenAnotherPendingAlreadyExists() {
        Fx fx = new Fx();
        fx.registerTrackingLastWordsSkill("LW_PENDING", 1);
        CardInstId candidateId = fx.addGraveCard("LW_PENDING");
        fx.player.pendingDecision(new PendingDecision.DiscardToHandLimit("existing", 5));

        LastWordsBatchCollector collector = new LastWordsBatchCollector(UUID.randomUUID());
        collector.register(candidateId);
        EffectContext ec = new EffectContext(
                fx.state,
                fx.ctx,
                fx.playerId,
                fixedId("00000000-0000-0000-0000-000000000001"),
                TargetSelection.empty(),
                List.of(),
                collector
        );
        List<GameEvent> events = new ArrayList<>();

        boolean opened = LastWordsDecisionOps.openPendingIfPossible(ec, fx.player, events);

        assertFalse(opened);
        assertInstanceOf(PendingDecision.DiscardToHandLimit.class, fx.player.pendingDecision());
        assertTrue(events.stream().anyMatch(event -> event instanceof GameEvent.LogAppended log
                && log.line().contains("pending decision already exists")));
    }

    @Test
    @DisplayName("grave에 실제 도착한 카드만 유언 후보로 등록된다")
    void onlyCardsThatArriveInGraveAreRegistered() {
        Fx fx = new Fx();
        fx.registerTrackingLastWordsToken("LW_TOKEN", 1);
        CardInstId tokenId = fx.addHandCard("LW_TOKEN");
        LastWordsBatchCollector collector = new LastWordsBatchCollector(UUID.randomUUID());
        EffectContext ec = new EffectContext(
                fx.state,
                fx.ctx,
                fx.playerId,
                fixedId("00000000-0000-0000-0000-000000000002"),
                TargetSelection.empty(),
                new ArrayList<>(),
                collector
        );

        boolean discarded = EffectDiscardOps.discardFromHandByEffect(ec, fx.player, tokenId);

        assertTrue(discarded);
        assertNull(fx.state.card(tokenId));
        assertFalse(collector.hasCandidates());
    }

    @Test
    @DisplayName("같은 resolve 타이밍의 modifier discard와 main effect discard가 하나의 collector를 공유한다")
    void sharedCollectorIncludesModifierAndMainEffectDiscards() {
        Fx fx = new Fx();
        fx.registerDiscardAllLastWordsSource("SOURCE", 0);
        fx.registerTrackingLastWordsSkill("LW_MODIFIER", 1);
        fx.registerTrackingLastWordsSkill("LW_MAIN", 1);

        CardInstId sourceId = fx.addHandCard(
                "SOURCE",
                fixedId("00000000-0000-0000-0000-000000000100"),
                List.of(new OwnedCardModifier(CardModifierIds.WEAKENED_DISCARD_ONE_SKILL, 1))
        );
        CardInstId modifierDiscardId = fx.addHandCard("LW_MODIFIER", fixedId("00000000-0000-0000-0000-000000000010"));
        CardInstId mainDiscardId = fx.addHandCard("LW_MAIN", fixedId("00000000-0000-0000-0000-000000000020"));

        EngineResult result = fx.play(sourceId);

        assertTrue(result.accepted());
        assertInstanceOf(PendingDecision.LastWordsChoice.class, fx.player.pendingDecision());
        PendingDecision.LastWordsChoice decision = (PendingDecision.LastWordsChoice) fx.player.pendingDecision();
        assertEquals(List.of(modifierDiscardId, mainDiscardId), decision.candidateIds());
    }

    @Test
    @DisplayName("EX effect discard opens LAST_WORDS pending")
    void useExOpensPendingForEffectDiscardedLastWordsCandidate() {
        Fx fx = new Fx();
        fx.registerDiscardAllLastWordsExSource("EX_SOURCE", 0);
        fx.registerTrackingLastWordsSkill("LW_EX", 1);

        fx.addExCard("EX_SOURCE");
        CardInstId lastWordsId = fx.addHandCard("LW_EX");

        EngineResult result = fx.process(new UseExCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                TargetSelection.empty()
        ));

        assertTrue(result.accepted());
        assertInstanceOf(PendingDecision.LastWordsChoice.class, fx.player.pendingDecision());
        PendingDecision.LastWordsChoice decision = (PendingDecision.LastWordsChoice) fx.player.pendingDecision();
        assertEquals(List.of(lastWordsId), decision.candidateIds());
        assertEquals(1, KeywordOps.keywordValue(fx.state, fx.ctx, lastWordsId, K014_LastWords.ID));
    }

    @Test
    @DisplayName("Summon action effect discard opens LAST_WORDS pending")
    void summonActionOpensPendingForEffectDiscardedLastWordsCandidate() {
        Fx fx = new Fx();
        fx.registerDiscardAllLastWordsSource("SUMMON_SOURCE", 0);
        fx.registerTrackingLastWordsSkill("LW_SUMMON", 1);

        CardInstId sourceId = fx.addFieldCard("SUMMON_SOURCE");
        com.example.dueltower.engine.model.Ids.SummonInstId summonId = fx.addSummon(sourceId, 1);
        CardInstId lastWordsId = fx.addHandCard("LW_SUMMON");

        EngineResult result = fx.process(new UseSummonActionCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                summonId,
                TargetSelection.empty()
        ));

        assertTrue(result.accepted());
        assertTrue(fx.state.summon(summonId).actionUsedThisTurn());
        assertEquals(2, fx.player.ap());
        assertInstanceOf(PendingDecision.LastWordsChoice.class, fx.player.pendingDecision());
        PendingDecision.LastWordsChoice decision = (PendingDecision.LastWordsChoice) fx.player.pendingDecision();
        assertEquals(List.of(lastWordsId), decision.candidateIds());
    }

    @Test
    @DisplayName("Pressure does not increase last words cost")
    void pressureDoesNotIncreaseLastWordsCost() {
        Fx fx = new Fx();
        fx.player.ap(1);
        fx.player.statusSet(S103_Pressure.ID, 3);
        fx.registerDiscardAllLastWordsSource("SUMMON_SOURCE", 0);
        fx.registerTrackingLastWordsSkill("LW_PRESSURE", 1);

        CardInstId sourceId = fx.addFieldCard("SUMMON_SOURCE");
        com.example.dueltower.engine.model.Ids.SummonInstId summonId = fx.addSummon(sourceId, 0);
        CardInstId lastWordsId = fx.addHandCard("LW_PRESSURE");

        EngineResult actionResult = fx.process(new UseSummonActionCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                summonId,
                TargetSelection.empty()
        ));

        assertTrue(actionResult.accepted());
        assertInstanceOf(PendingDecision.LastWordsChoice.class, fx.player.pendingDecision());
        assertEquals(List.of(lastWordsId), ((PendingDecision.LastWordsChoice) fx.player.pendingDecision()).candidateIds());

        EngineResult resolveResult = fx.process(new ResolveLastWordsCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                List.of(lastWordsId)
        ));

        assertTrue(resolveResult.accepted());
        assertEquals(0, fx.player.ap());
    }

    @Test
    @DisplayName("Pressure does not increase summon action cost")
    void pressureDoesNotIncreaseSummonActionCost() {
        Fx fx = new Fx();
        fx.player.ap(1);
        fx.player.statusSet(S103_Pressure.ID, 3);
        fx.registerPlainSkill("SUMMON_SOURCE", 0);

        CardInstId sourceId = fx.addFieldCard("SUMMON_SOURCE");
        com.example.dueltower.engine.model.Ids.SummonInstId summonId = fx.addSummon(sourceId, 1);

        EngineResult result = fx.process(new UseSummonActionCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                summonId,
                TargetSelection.empty()
        ));

        assertTrue(result.accepted());
        assertEquals(0, fx.player.ap());
        assertTrue(fx.state.summon(summonId).actionUsedThisTurn());
    }

    @Test
    @DisplayName("EX effect-discard濡?踰꾨┛ ?좎뼵 移대뱶媛 AP 遺議깊븯硫?pending ?꾨낫?먯꽌 ?쒖쇅?쒕떎")
    void useExFiltersOutUnpayableLastWordsCandidate() {
        Fx fx = new Fx();
        fx.player.ap(1);
        fx.registerDiscardAllLastWordsExSource("EX_SOURCE", 0);
        fx.registerTrackingLastWordsSkill("LW_EXPENSIVE_EX", 2);

        fx.addExCard("EX_SOURCE");
        fx.addHandCard("LW_EXPENSIVE_EX");

        EngineResult result = fx.process(new UseExCommand(
                UUID.randomUUID(),
                fx.state.version(),
                fx.playerId,
                TargetSelection.empty()
        ));

        assertTrue(result.accepted());
        assertNull(fx.player.pendingDecision());
        assertTrue(result.events().stream().anyMatch(event -> event instanceof GameEvent.LogAppended log
                && log.line().contains("no payable candidates")));
    }

    private static final class Fx {
        private final GameState state = new GameState(new SessionId(UUID.randomUUID()), 321L);
        private final GameEngine engine = new GameEngine();
        private final PlayerId playerId = new PlayerId("P1");
        private final PlayerState player = new PlayerState(playerId);
        private final EnemyId enemyId = new EnemyId("E1");
        private final EnemyState enemy = new EnemyState(enemyId, 20);
        private final Map<CardDefId, CardDefinition> defs = new LinkedHashMap<>();
        private final Map<CardDefId, CardEffect> effects = new LinkedHashMap<>();
        private final Map<String, CardModifierDefinition> cardModifierDefs = new LinkedHashMap<>();
        private final Map<String, com.example.dueltower.engine.core.effect.cardmodifier.CardModifierEffect> cardModifierEffects = new LinkedHashMap<>();
        private EngineContext ctx;

        private Fx() {
            state.players().put(playerId, player);
            state.enemies().put(enemyId, enemy);
            player.ap(3);
            setupCombatTurn();
            registerModifier(new CM105_WeakenedDiscardOneSkill());
            refreshContext();
        }

        private void setupCombatTurn() {
            CombatState combat = new CombatState();
            combat.phase(CombatPhase.MAIN);
            combat.turnOrder().add(TargetRef.ofPlayer(playerId));
            state.combat(combat);
        }

        private void registerDiscardAllLastWordsSource(String id, int cost) {
            registerCard(id, CardType.SKILL, cost, Map.of(), false, new DiscardAllLastWordsInHandEffect());
        }

        private void registerDiscardAllLastWordsExSource(String id, int cost) {
            registerCard(id, CardType.EX, cost, Map.of(), false, new DiscardAllLastWordsInHandEffect());
        }

        private void registerPlainSkill(String id, int cost) {
            registerCard(id, CardType.SKILL, cost, Map.of(), false, new NoOpEffect(id));
        }

        private void registerTrackingLastWordsSkill(String id, int cost) {
            registerCard(id, CardType.SKILL, 0, Map.of(K014_LastWords.ID, cost), false, new TrackingLastWordsEffect(id));
        }

        private void registerTrackingLastWordsToken(String id, int cost) {
            registerCard(id, CardType.SKILL, 0, Map.of(K014_LastWords.ID, cost), true, new TrackingLastWordsEffect(id));
        }

        private void registerCard(String id, CardType type, int cost, Map<String, Integer> keywords, boolean token, CardEffect effect) {
            CardDefId defId = new CardDefId(id);
            defs.put(defId, new CardDefinition(defId, id, type, cost, keywords, Zone.GRAVE, token, ""));
            effects.put(defId, effect);
            refreshContext();
        }

        private void registerModifier(CM105_WeakenedDiscardOneSkill modifier) {
            cardModifierDefs.put(modifier.id(), modifier.definition());
            cardModifierEffects.put(modifier.id(), modifier);
        }

        private void refreshContext() {
            ctx = new EngineContext(
                    defs,
                    effects,
                    Map.of(S103_Pressure.ID, new S103_Pressure().definition()),
                    Map.of(S103_Pressure.ID, new S103_Pressure()),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    Map.of(),
                    cardModifierDefs,
                    cardModifierEffects,
                    Map.of(),
                    Map.of(),
                    Map.of()
            );
        }

        private CardInstId addHandCard(String defId) {
            return addHandCard(defId, new CardInstId(UUID.randomUUID()), List.of());
        }

        private CardInstId addHandCard(String defId, CardInstId instanceId) {
            return addHandCard(defId, instanceId, List.of());
        }

        private CardInstId addHandCard(String defId, CardInstId instanceId, List<OwnedCardModifier> modifiers) {
            CardDefId cardDefId = new CardDefId(defId);
            CardInstance ci = new CardInstance(instanceId, cardDefId, playerId, Zone.HAND, null, modifiers);
            state.cardInstances().put(instanceId, ci);
            player.hand().add(instanceId);
            return instanceId;
        }

        private CardInstId addFieldCard(String defId) {
            CardInstId instanceId = new CardInstId(UUID.randomUUID());
            CardDefId cardDefId = new CardDefId(defId);
            state.cardInstances().put(instanceId, new CardInstance(instanceId, cardDefId, playerId, Zone.FIELD));
            player.field().add(instanceId);
            return instanceId;
        }

        private CardInstId addExCard(String defId) {
            CardInstId instanceId = new CardInstId(UUID.randomUUID());
            CardDefId cardDefId = new CardDefId(defId);
            state.cardInstances().put(instanceId, new CardInstance(instanceId, cardDefId, playerId, Zone.EX));
            player.exCard(instanceId);
            return instanceId;
        }

        private com.example.dueltower.engine.model.Ids.SummonInstId addSummon(CardInstId sourceCardId, int actionCost) {
            com.example.dueltower.engine.model.Ids.SummonInstId summonId =
                    new com.example.dueltower.engine.model.Ids.SummonInstId(UUID.randomUUID());
            SummonState summon = new SummonState(summonId, playerId, sourceCardId, 5, 5, 0, 0, actionCost, false);
            state.summons().put(summonId, summon);
            player.activeSummons().add(summonId);
            player.summonByCard().put(sourceCardId, summonId);
            return summonId;
        }

        private CardInstId addDeckCard(String defId) {
            CardInstId instanceId = new CardInstId(UUID.randomUUID());
            CardDefId cardDefId = new CardDefId(defId);
            state.cardInstances().put(instanceId, new CardInstance(instanceId, cardDefId, playerId, Zone.DECK));
            player.deck().addLast(instanceId);
            return instanceId;
        }

        private CardInstId addGraveCard(String defId) {
            CardInstId instanceId = new CardInstId(UUID.randomUUID());
            CardDefId cardDefId = new CardDefId(defId);
            state.cardInstances().put(instanceId, new CardInstance(instanceId, cardDefId, playerId, Zone.GRAVE));
            player.grave().add(instanceId);
            return instanceId;
        }

        private EngineResult play(CardInstId sourceId) {
            return process(new PlayCardCommand(
                    UUID.randomUUID(),
                    state.version(),
                    playerId,
                    sourceId,
                    TargetSelection.empty()
            ));
        }

        private EngineResult process(com.example.dueltower.engine.command.GameCommand command) {
            return engine.process(state, ctx, command);
        }
    }

    private static final class DiscardAllLastWordsInHandEffect implements CardEffect {
        @Override
        public String id() {
            return "DISCARD_ALL_LAST_WORDS";
        }

        @Override
        public void resolve(EffectContext ec) {
            PlayerState owner = ec.state().player(ec.actor());
            List<CardInstId> snapshot = List.copyOf(owner.hand());
            for (CardInstId id : snapshot) {
                if (id.equals(ec.cardId())) {
                    continue;
                }
                if (KeywordOps.keywordValue(ec.state(), ec.ctx(), id, K014_LastWords.ID) <= 0) {
                    continue;
                }
                EffectDiscardOps.discardFromHandByEffect(ec, owner, id);
            }
        }
    }

    private static class NoOpEffect implements CardEffect {
        private final String id;

        private NoOpEffect(String id) {
            this.id = id;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void resolve(EffectContext ec) {
        }
    }

    private static final class TrackingLastWordsEffect extends NoOpEffect {
        private TrackingLastWordsEffect(String id) {
            super(id);
        }
    }

    private static CardInstId fixedId(String raw) {
        return new CardInstId(UUID.fromString(raw));
    }
}
