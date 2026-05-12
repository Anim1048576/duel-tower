package com.example.dueltower.engine;

import com.example.dueltower.content.card.cdb.player.nameless.Nameless001_Card;
import com.example.dueltower.content.card.cdb.player.nameless.Nameless002_Card;
import com.example.dueltower.content.card.cdb.player.nameless.Nameless003_Card;
import com.example.dueltower.content.card.cdb.player.nameless.Nameless004_Card;
import com.example.dueltower.content.card.cdb.player.nameless.Nameless005_Card;
import com.example.dueltower.content.card.cdb.player.nameless.Nameless006_Card;
import com.example.dueltower.content.card.cdb.player.nameless.Nameless901_EX;
import com.example.dueltower.content.card.cdb.player.tig.Tig901_EX;
import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.keyword.kdb.K007_ClearMind;
import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.content.passive.pdb.player.nameless.Nameless001_Passive;
import com.example.dueltower.content.passive.pdb.player.nameless.Nameless002_Passive;
import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.content.status.sdb.S001_Shield;
import com.example.dueltower.content.status.sdb.S002_Regeneration;
import com.example.dueltower.content.status.sdb.S003_Vigor;
import com.example.dueltower.content.status.sdb.S005_Taunt;
import com.example.dueltower.content.status.sdb.S101_Pain;
import com.example.dueltower.content.status.sdb.S104_Destruction;
import com.example.dueltower.content.status.sdb.S106_Vulnerable;
import com.example.dueltower.content.status.sdb.player.nameless.Nameless201_Entropy;
import com.example.dueltower.content.status.sdb.player.nameless.Nameless202_EventHorizon;
import com.example.dueltower.content.status.sdb.player.nameless.Nameless203_EventHorizonUsed;
import com.example.dueltower.engine.command.PlayCardCommand;
import com.example.dueltower.engine.command.ResolveEventHorizonCommand;
import com.example.dueltower.engine.command.UseExCommand;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.engine.core.GameEngine;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.keyword.KeywordEffect;
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
import com.example.dueltower.engine.model.KeywordDefinition;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.engine.model.StatusDefinition;
import com.example.dueltower.engine.model.TargetRef;
import com.example.dueltower.engine.model.TargetSelection;
import com.example.dueltower.engine.model.Zone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class NamelessRegressionFixture {
    static final Ids.PlayerId PLAYER_ID = new Ids.PlayerId("nameless");
    static final Ids.PlayerId OTHER_ID = new Ids.PlayerId("other");
    static final Ids.EnemyId ENEMY_ID = new Ids.EnemyId("enemy");
    static final Ids.CardDefId TEST_SKILL_ID = new Ids.CardDefId("Nameless_RegressionSkill");
    static final Ids.CardDefId TEST_EX_ID = new Ids.CardDefId("Nameless_RegressionEx");
    static final Ids.CardDefId FILLER_ID = new Ids.CardDefId("Nameless_RegressionFiller");

    final Nameless001_Card space = new Nameless001_Card();
    final Nameless002_Card universe = new Nameless002_Card();
    final Nameless003_Card cosmos = new Nameless003_Card();
    final Nameless004_Card chaos = new Nameless004_Card();
    final Nameless005_Card voidCard = new Nameless005_Card();
    final Nameless006_Card empty = new Nameless006_Card();
    final Nameless901_EX namelessEx = new Nameless901_EX();
    final Tig901_EX tigEx = new Tig901_EX();

    final GameEngine engine = new GameEngine();
    final GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 20260512L);
    final PlayerState player = new PlayerState(PLAYER_ID);
    final PlayerState other = new PlayerState(OTHER_ID);
    final EnemyState enemy = new EnemyState(ENEMY_ID, 30);
    final List<GameEvent> events = new ArrayList<>();
    final EngineContext ctx;
    final Map<Ids.CardDefId, CardDefinition> definitions = new HashMap<>();

    NamelessRegressionFixture() {
        state.players().put(PLAYER_ID, player);
        state.players().put(OTHER_ID, other);
        state.enemies().put(ENEMY_ID, enemy);
        startCombat();

        Map<Ids.CardDefId, CardEffect> cardEffects = new HashMap<>();
        registerCard(definitions, cardEffects, space);
        registerCard(definitions, cardEffects, universe);
        registerCard(definitions, cardEffects, cosmos);
        registerCard(definitions, cardEffects, chaos);
        registerCard(definitions, cardEffects, voidCard);
        registerCard(definitions, cardEffects, empty);
        registerCard(definitions, cardEffects, namelessEx);
        registerCard(definitions, cardEffects, tigEx);
        definitions.put(TEST_SKILL_ID, new CardDefinition(TEST_SKILL_ID, "Test Skill", CardType.SKILL, 0, Map.of(), Zone.GRAVE, false, ""));
        definitions.put(TEST_EX_ID, new CardDefinition(TEST_EX_ID, "Test EX", CardType.EX, 0, Map.of(), Zone.EX, false, ""));
        definitions.put(FILLER_ID, new CardDefinition(FILLER_ID, "Filler", CardType.SKILL, 0, Map.of(), Zone.GRAVE, false, ""));

        Map<String, StatusDefinition> statusDefs = new HashMap<>();
        Map<String, StatusEffect> statusEffects = new HashMap<>();
        registerStatus(statusDefs, statusEffects, new S001_Shield());
        registerStatus(statusDefs, statusEffects, new S002_Regeneration());
        registerStatus(statusDefs, statusEffects, new S003_Vigor());
        registerStatus(statusDefs, statusEffects, new S005_Taunt());
        registerStatus(statusDefs, statusEffects, new S101_Pain());
        registerStatus(statusDefs, statusEffects, new S104_Destruction());
        registerStatus(statusDefs, statusEffects, new S106_Vulnerable());
        registerStatus(statusDefs, statusEffects, new Nameless201_Entropy());
        registerStatus(statusDefs, statusEffects, new Nameless202_EventHorizon());
        registerStatus(statusDefs, statusEffects, new Nameless203_EventHorizonUsed());

        KeywordBlueprint clearMind = new K007_ClearMind();
        Map<String, KeywordDefinition> keywordDefs = Map.of(clearMind.id(), clearMind.definition());
        Map<String, KeywordEffect> keywordEffects = Map.of(clearMind.id(), clearMind);

        Nameless001_Passive resonance = new Nameless001_Passive();
        Nameless002_Passive emission = new Nameless002_Passive();
        Map<String, com.example.dueltower.engine.model.PassiveDefinition> passiveDefs = Map.of(
                resonance.id(), resonance.definition(),
                emission.id(), emission.definition()
        );
        Map<String, PassiveEffect> passiveEffects = Map.of(
                resonance.id(), resonance,
                emission.id(), emission
        );

        ctx = new EngineContext(
                definitions,
                cardEffects,
                statusDefs,
                statusEffects,
                keywordDefs,
                keywordEffects,
                passiveDefs,
                passiveEffects
        );
    }

    void startCombat() {
        CombatState combat = new CombatState();
        combat.turnOrder().add(TargetRef.ofPlayer(PLAYER_ID));
        combat.turnOrder().add(TargetRef.ofEnemy(ENEMY_ID));
        combat.currentTurnIndex(0);
        combat.phase(CombatPhase.MAIN);
        combat.round(1);
        state.combat(combat);
    }

    TargetRef selfRef() {
        return TargetRef.ofPlayer(PLAYER_ID);
    }

    TargetRef otherRef() {
        return TargetRef.ofPlayer(OTHER_ID);
    }

    TargetRef enemyRef() {
        return TargetRef.ofEnemy(ENEMY_ID);
    }

    TargetSelection selfTarget() {
        return new TargetSelection(List.of(selfRef()));
    }

    TargetSelection otherTarget() {
        return new TargetSelection(List.of(otherRef()));
    }

    TargetSelection enemyTarget() {
        return new TargetSelection(List.of(enemyRef()));
    }

    void setPassives(String... passiveIds) {
        player.passiveIds(List.of(passiveIds));
    }

    Ids.CardInstId addHandCard(Ids.CardDefId defId) {
        return addCard(defId, Zone.HAND);
    }

    Ids.CardInstId addDeckCard(Ids.CardDefId defId) {
        return addCard(defId, Zone.DECK);
    }

    Ids.CardInstId setNamelessEx() {
        return addCard(new Ids.CardDefId(namelessEx.id()), Zone.EX);
    }

    Ids.CardInstId setTigEx() {
        return addCard(new Ids.CardDefId(tigEx.id()), Zone.EX);
    }

    Ids.CardInstId sourceCard(CardBlueprint card) {
        Ids.CardDefId defId = new Ids.CardDefId(card.id());
        return addHandCard(defId);
    }

    Ids.CardInstId addCard(Ids.CardDefId defId, Zone zone) {
        Ids.CardInstId cardId = new Ids.CardInstId(UUID.randomUUID());
        state.cardInstances().put(cardId, new CardInstance(cardId, defId, PLAYER_ID, zone));
        switch (zone) {
            case HAND -> player.hand().add(cardId);
            case DECK -> player.deck().addLast(cardId);
            case GRAVE -> player.grave().add(cardId);
            case FIELD -> player.field().add(cardId);
            case EXCLUDED -> player.excluded().add(cardId);
            case EX -> player.exCard(cardId);
        }
        return cardId;
    }

    EffectContext effectContext(Ids.CardInstId cardId, TargetSelection selection) {
        return new EffectContext(state, ctx, PLAYER_ID, cardId, selection, events);
    }

    EffectContext effectContext(CardBlueprint card, TargetSelection selection, List<Ids.CardInstId> discardIds) {
        return new EffectContext(state, ctx, PLAYER_ID, sourceCard(card), selection, discardIds, events);
    }

    EffectContext effectContext(CardBlueprint card, TargetSelection selection, String choiceId) {
        return new EffectContext(state, ctx, PLAYER_ID, sourceCard(card), selection, choiceId, events);
    }

    void resolve(CardBlueprint card, TargetSelection selection) {
        card.resolve(effectContext(card, selection, List.of()));
    }

    void resolve(CardBlueprint card, TargetSelection selection, List<Ids.CardInstId> discardIds) {
        card.resolve(effectContext(card, selection, discardIds));
    }

    EngineResult play(Ids.CardInstId cardId, TargetSelection selection) {
        return engine.process(state, ctx, new PlayCardCommand(UUID.randomUUID(), state.version(), PLAYER_ID, cardId, selection));
    }

    EngineResult play(Ids.CardInstId cardId, TargetSelection selection, List<Ids.CardInstId> discardIds) {
        return engine.process(state, ctx, new PlayCardCommand(UUID.randomUUID(), state.version(), PLAYER_ID, cardId, selection, discardIds));
    }

    EngineResult useNamelessEx(TargetSelection selection, String choiceId) {
        return engine.process(state, ctx, new UseExCommand(UUID.randomUUID(), state.version(), PLAYER_ID, selection, choiceId));
    }

    EngineResult useTigEx(TargetSelection selection) {
        return engine.process(state, ctx, new UseExCommand(UUID.randomUUID(), state.version(), PLAYER_ID, selection));
    }

    EngineResult resolveEventHorizon(String choiceId) {
        return engine.process(state, ctx, new ResolveEventHorizonCommand(UUID.randomUUID(), state.version(), PLAYER_ID, choiceId));
    }

    private static void registerCard(
            Map<Ids.CardDefId, CardDefinition> defs,
            Map<Ids.CardDefId, CardEffect> effects,
            CardBlueprint card
    ) {
        defs.put(card.definition().id(), card.definition());
        effects.put(card.definition().id(), card);
    }

    private static void registerStatus(
            Map<String, StatusDefinition> defs,
            Map<String, StatusEffect> effects,
            StatusBlueprint status
    ) {
        defs.put(status.id(), status.definition());
        effects.put(status.id(), status);
    }
}
