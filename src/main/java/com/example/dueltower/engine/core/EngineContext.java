package com.example.dueltower.engine.core;

import com.example.dueltower.config.GameRules;
import com.example.dueltower.config.RewardTableConfig;
import com.example.dueltower.engine.config.EncounterTableConfig;
import com.example.dueltower.engine.config.RunConfig;
import com.example.dueltower.engine.config.RunConfigs;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.core.effect.cardmodifier.CardModifierEffect;
import com.example.dueltower.engine.core.effect.item.ItemEffect;
import com.example.dueltower.engine.core.effect.keyword.KeywordEffect;
import com.example.dueltower.engine.core.effect.passive.PassiveEffect;
import com.example.dueltower.engine.core.effect.status.StatusEffect;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardModifierDefinition;
import com.example.dueltower.engine.model.EquipDefinition;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.ItemDefinition;
import com.example.dueltower.engine.model.KeywordDefinition;
import com.example.dueltower.engine.model.PassiveDefinition;
import com.example.dueltower.engine.model.StatusDefinition;

import java.util.Map;
import java.util.Objects;

public final class EngineContext {
    private final Map<CardDefId, CardDefinition> definitions;
    private final Map<CardDefId, CardEffect> effects;

    private final Map<String, StatusDefinition> statusDefs;
    private final Map<String, StatusEffect> statusEffects;

    private final Map<String, KeywordDefinition> keywordDefs;
    private final Map<String, KeywordEffect> keywordEffects;

    private final Map<String, PassiveDefinition> passiveDefs;
    private final Map<String, PassiveEffect> passiveEffects;

    private final Map<String, CardModifierDefinition> cardModifierDefs;
    private final Map<String, CardModifierEffect> cardModifierEffects;

    private final Map<String, ItemDefinition> itemDefs;
    private final Map<String, ItemEffect> itemEffects;
    private final Map<String, EquipDefinition> equipDefs;
    private final GameRules gameRules;
    private final RewardTableConfig rewardTableConfig;
    private final EncounterTableConfig encounterTableConfig;
    private final RunConfig runConfig;
    private final boolean resolvingReaction;

    public EngineContext(Map<CardDefId, CardDefinition> definitions, Map<CardDefId, CardEffect> effects) {
        this(definitions, effects, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), GameRules.defaults(), RewardTableConfig.defaults(), EncounterTableConfig.defaults());
    }

    public EngineContext(
            Map<CardDefId, CardDefinition> definitions,
            Map<CardDefId, CardEffect> effects,
            Map<String, StatusDefinition> statusDefs,
            Map<String, StatusEffect> statusEffects
    ) {
        this(definitions, effects, statusDefs, statusEffects, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), GameRules.defaults(), RewardTableConfig.defaults(), EncounterTableConfig.defaults());
    }

    public EngineContext(
            Map<CardDefId, CardDefinition> definitions,
            Map<CardDefId, CardEffect> effects,
            Map<String, StatusDefinition> statusDefs,
            Map<String, StatusEffect> statusEffects,
            Map<String, KeywordDefinition> keywordDefs,
            Map<String, KeywordEffect> keywordEffects
    ) {
        this(definitions, effects, statusDefs, statusEffects, keywordDefs, keywordEffects, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), GameRules.defaults(), RewardTableConfig.defaults(), EncounterTableConfig.defaults());
    }

    public EngineContext(
            Map<CardDefId, CardDefinition> definitions,
            Map<CardDefId, CardEffect> effects,
            Map<String, StatusDefinition> statusDefs,
            Map<String, StatusEffect> statusEffects,
            Map<String, KeywordDefinition> keywordDefs,
            Map<String, KeywordEffect> keywordEffects,
            Map<String, PassiveDefinition> passiveDefs,
            Map<String, PassiveEffect> passiveEffects
    ) {
        this(definitions, effects, statusDefs, statusEffects, keywordDefs, keywordEffects, passiveDefs, passiveEffects, Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), GameRules.defaults(), RewardTableConfig.defaults(), EncounterTableConfig.defaults());
    }

    public EngineContext(
            Map<CardDefId, CardDefinition> definitions,
            Map<CardDefId, CardEffect> effects,
            Map<String, StatusDefinition> statusDefs,
            Map<String, StatusEffect> statusEffects,
            Map<String, KeywordDefinition> keywordDefs,
            Map<String, KeywordEffect> keywordEffects,
            Map<String, PassiveDefinition> passiveDefs,
            Map<String, PassiveEffect> passiveEffects,
            Map<String, CardModifierDefinition> cardModifierDefs,
            Map<String, CardModifierEffect> cardModifierEffects
    ) {
        this(definitions, effects, statusDefs, statusEffects, keywordDefs, keywordEffects, passiveDefs, passiveEffects, cardModifierDefs, cardModifierEffects, Map.of(), Map.of(), Map.of(), GameRules.defaults(), RewardTableConfig.defaults(), EncounterTableConfig.defaults());
    }

    public EngineContext(
            Map<CardDefId, CardDefinition> definitions,
            Map<CardDefId, CardEffect> effects,
            Map<String, StatusDefinition> statusDefs,
            Map<String, StatusEffect> statusEffects,
            Map<String, KeywordDefinition> keywordDefs,
            Map<String, KeywordEffect> keywordEffects,
            Map<String, PassiveDefinition> passiveDefs,
            Map<String, PassiveEffect> passiveEffects,
            Map<String, CardModifierDefinition> cardModifierDefs,
            Map<String, CardModifierEffect> cardModifierEffects,
            Map<String, ItemDefinition> itemDefs,
            Map<String, ItemEffect> itemEffects
    ) {
        this(definitions, effects, statusDefs, statusEffects, keywordDefs, keywordEffects, passiveDefs, passiveEffects,
                cardModifierDefs, cardModifierEffects, itemDefs, itemEffects, Map.of(), GameRules.defaults(), RewardTableConfig.defaults(), EncounterTableConfig.defaults());
    }

    public EngineContext(
            Map<CardDefId, CardDefinition> definitions,
            Map<CardDefId, CardEffect> effects,
            Map<String, StatusDefinition> statusDefs,
            Map<String, StatusEffect> statusEffects,
            Map<String, KeywordDefinition> keywordDefs,
            Map<String, KeywordEffect> keywordEffects,
            Map<String, PassiveDefinition> passiveDefs,
            Map<String, PassiveEffect> passiveEffects,
            Map<String, CardModifierDefinition> cardModifierDefs,
            Map<String, CardModifierEffect> cardModifierEffects,
            Map<String, ItemDefinition> itemDefs,
            Map<String, ItemEffect> itemEffects,
            Map<String, EquipDefinition> equipDefs
    ) {
        this(definitions, effects, statusDefs, statusEffects, keywordDefs, keywordEffects, passiveDefs, passiveEffects,
                cardModifierDefs, cardModifierEffects, itemDefs, itemEffects, equipDefs, GameRules.defaults(), RewardTableConfig.defaults(), EncounterTableConfig.defaults());
    }

    public EngineContext(
            Map<CardDefId, CardDefinition> definitions,
            Map<CardDefId, CardEffect> effects,
            Map<String, StatusDefinition> statusDefs,
            Map<String, StatusEffect> statusEffects,
            Map<String, KeywordDefinition> keywordDefs,
            Map<String, KeywordEffect> keywordEffects,
            Map<String, PassiveDefinition> passiveDefs,
            Map<String, PassiveEffect> passiveEffects,
            Map<String, CardModifierDefinition> cardModifierDefs,
            Map<String, CardModifierEffect> cardModifierEffects,
            Map<String, ItemDefinition> itemDefs,
            Map<String, ItemEffect> itemEffects,
            Map<String, EquipDefinition> equipDefs,
            GameRules gameRules
    ) {
        this(definitions, effects, statusDefs, statusEffects, keywordDefs, keywordEffects, passiveDefs, passiveEffects,
                cardModifierDefs, cardModifierEffects, itemDefs, itemEffects, equipDefs, gameRules, RewardTableConfig.defaults(), EncounterTableConfig.defaults());
    }

    public EngineContext(
            Map<CardDefId, CardDefinition> definitions,
            Map<CardDefId, CardEffect> effects,
            Map<String, StatusDefinition> statusDefs,
            Map<String, StatusEffect> statusEffects,
            Map<String, KeywordDefinition> keywordDefs,
            Map<String, KeywordEffect> keywordEffects,
            Map<String, PassiveDefinition> passiveDefs,
            Map<String, PassiveEffect> passiveEffects,
            Map<String, CardModifierDefinition> cardModifierDefs,
            Map<String, CardModifierEffect> cardModifierEffects,
            Map<String, ItemDefinition> itemDefs,
            Map<String, ItemEffect> itemEffects,
            Map<String, EquipDefinition> equipDefs,
            GameRules gameRules,
            RewardTableConfig rewardTableConfig,
            EncounterTableConfig encounterTableConfig
    ) {
        this(definitions, effects, statusDefs, statusEffects, keywordDefs, keywordEffects, passiveDefs, passiveEffects,
                cardModifierDefs, cardModifierEffects, itemDefs, itemEffects, equipDefs, gameRules, rewardTableConfig,
                encounterTableConfig, RunConfigs.defaultConfig());
    }

    public EngineContext(
            Map<CardDefId, CardDefinition> definitions,
            Map<CardDefId, CardEffect> effects,
            Map<String, StatusDefinition> statusDefs,
            Map<String, StatusEffect> statusEffects,
            Map<String, KeywordDefinition> keywordDefs,
            Map<String, KeywordEffect> keywordEffects,
            Map<String, PassiveDefinition> passiveDefs,
            Map<String, PassiveEffect> passiveEffects,
            Map<String, CardModifierDefinition> cardModifierDefs,
            Map<String, CardModifierEffect> cardModifierEffects,
            Map<String, ItemDefinition> itemDefs,
            Map<String, ItemEffect> itemEffects,
            Map<String, EquipDefinition> equipDefs,
            GameRules gameRules,
            RewardTableConfig rewardTableConfig,
            EncounterTableConfig encounterTableConfig,
            RunConfig runConfig
    ) {
        this(definitions, effects, statusDefs, statusEffects, keywordDefs, keywordEffects, passiveDefs, passiveEffects,
                cardModifierDefs, cardModifierEffects, itemDefs, itemEffects, equipDefs, gameRules, rewardTableConfig,
                encounterTableConfig, runConfig, false);
    }

    private EngineContext(
            Map<CardDefId, CardDefinition> definitions,
            Map<CardDefId, CardEffect> effects,
            Map<String, StatusDefinition> statusDefs,
            Map<String, StatusEffect> statusEffects,
            Map<String, KeywordDefinition> keywordDefs,
            Map<String, KeywordEffect> keywordEffects,
            Map<String, PassiveDefinition> passiveDefs,
            Map<String, PassiveEffect> passiveEffects,
            Map<String, CardModifierDefinition> cardModifierDefs,
            Map<String, CardModifierEffect> cardModifierEffects,
            Map<String, ItemDefinition> itemDefs,
            Map<String, ItemEffect> itemEffects,
            Map<String, EquipDefinition> equipDefs,
            GameRules gameRules,
            RewardTableConfig rewardTableConfig,
            EncounterTableConfig encounterTableConfig,
            RunConfig runConfig,
            boolean resolvingReaction
    ) {
        this.definitions = Map.copyOf(definitions);
        this.effects = Map.copyOf(effects);
        this.statusDefs = Map.copyOf(statusDefs);
        this.statusEffects = Map.copyOf(statusEffects);
        this.keywordDefs = Map.copyOf(keywordDefs);
        this.keywordEffects = Map.copyOf(keywordEffects);
        this.passiveDefs = Map.copyOf(passiveDefs);
        this.passiveEffects = Map.copyOf(passiveEffects);
        this.cardModifierDefs = Map.copyOf(cardModifierDefs);
        this.cardModifierEffects = Map.copyOf(cardModifierEffects);
        this.itemDefs = Map.copyOf(itemDefs);
        this.itemEffects = Map.copyOf(itemEffects);
        this.equipDefs = Map.copyOf(equipDefs);
        this.gameRules = Objects.requireNonNull(gameRules, "gameRules");
        this.rewardTableConfig = Objects.requireNonNull(rewardTableConfig, "rewardTableConfig");
        this.encounterTableConfig = Objects.requireNonNull(encounterTableConfig, "encounterTableConfig");
        this.runConfig = Objects.requireNonNull(runConfig, "runConfig");
        this.resolvingReaction = resolvingReaction;
    }

    public EngineContext(
            Map<CardDefId, CardDefinition> definitions,
            Map<CardDefId, CardEffect> effects,
            Map<String, StatusDefinition> statusDefs,
            Map<String, StatusEffect> statusEffects,
            Map<String, KeywordDefinition> keywordDefs,
            Map<String, KeywordEffect> keywordEffects,
            Map<String, PassiveDefinition> passiveDefs,
            Map<String, PassiveEffect> passiveEffects,
            Map<String, CardModifierDefinition> cardModifierDefs,
            Map<String, CardModifierEffect> cardModifierEffects,
            Map<String, ItemDefinition> itemDefs,
            Map<String, ItemEffect> itemEffects,
            Map<String, EquipDefinition> equipDefs,
            GameRules gameRules,
            RewardTableConfig rewardTableConfig
    ) {
        this(definitions, effects, statusDefs, statusEffects, keywordDefs, keywordEffects, passiveDefs, passiveEffects,
                cardModifierDefs, cardModifierEffects, itemDefs, itemEffects, equipDefs, gameRules, rewardTableConfig, EncounterTableConfig.defaults());
    }

    public CardDefinition def(CardDefId id) {
        CardDefinition d = definitions.get(id);
        if (d == null) throw new IllegalArgumentException("missing card definition: " + id.value());
        return d;
    }

    public boolean hasEffect(CardDefId defId) { return effects.containsKey(defId); }

    public CardEffect effect(CardDefId defId) {
        CardEffect e = effects.get(defId);
        if (e == null) throw new IllegalArgumentException("missing CardEffect: " + defId.value());
        return e;
    }

    public boolean hasStatusDef(String id) { return statusDefs.containsKey(id); }
    public StatusDefinition statusDef(String id) {
        StatusDefinition d = statusDefs.get(id);
        if (d == null) throw new IllegalArgumentException("missing StatusDefinition: " + id);
        return d;
    }

    public boolean hasStatusEffect(String id) { return statusEffects.containsKey(id); }
    public StatusEffect statusEffect(String id) {
        StatusEffect e = statusEffects.get(id);
        if (e == null) throw new IllegalArgumentException("missing StatusEffect: " + id);
        return e;
    }

    public boolean hasKeywordDef(String id) { return keywordDefs.containsKey(id); }
    public KeywordDefinition keywordDef(String id) {
        KeywordDefinition d = keywordDefs.get(id);
        if (d == null) throw new IllegalArgumentException("missing KeywordDefinition: " + id);
        return d;
    }

    public boolean hasKeywordEffect(String id) { return keywordEffects.containsKey(id); }
    public KeywordEffect keywordEffect(String id) {
        KeywordEffect e = keywordEffects.get(id);
        if (e == null) throw new IllegalArgumentException("missing KeywordEffect: " + id);
        return e;
    }

    public boolean hasPassiveDef(String id) { return passiveDefs.containsKey(id); }
    public PassiveDefinition passiveDef(String id) {
        PassiveDefinition d = passiveDefs.get(id);
        if (d == null) throw new IllegalArgumentException("missing PassiveDefinition: " + id);
        return d;
    }

    public boolean hasPassiveEffect(String id) { return passiveEffects.containsKey(id); }
    public PassiveEffect passiveEffect(String id) {
        PassiveEffect e = passiveEffects.get(id);
        if (e == null) throw new IllegalArgumentException("missing PassiveEffect: " + id);
        return e;
    }

    public boolean hasCardModifierDef(String id) { return cardModifierDefs.containsKey(id); }
    public CardModifierDefinition cardModifierDef(String id) {
        CardModifierDefinition d = cardModifierDefs.get(id);
        if (d == null) throw new IllegalArgumentException("missing CardModifierDefinition: " + id);
        return d;
    }

    public boolean hasCardModifierEffect(String id) { return cardModifierEffects.containsKey(id); }
    public CardModifierEffect cardModifierEffect(String id) {
        CardModifierEffect e = cardModifierEffects.get(id);
        if (e == null) throw new IllegalArgumentException("missing CardModifierEffect: " + id);
        return e;
    }

    public boolean hasItemDef(String id) { return itemDefs.containsKey(id); }
    public ItemDefinition itemDef(String id) {
        ItemDefinition d = itemDefs.get(id);
        if (d == null) throw new IllegalArgumentException("missing ItemDefinition: " + id);
        return d;
    }

    public boolean hasItemEffect(String id) { return itemEffects.containsKey(id); }
    public ItemEffect itemEffect(String id) {
        ItemEffect e = itemEffects.get(id);
        if (e == null) throw new IllegalArgumentException("missing ItemEffect: " + id);
        return e;
    }

    public boolean hasEquipDef(String id) { return equipDefs.containsKey(id); }
    public EquipDefinition equipDef(String id) {
        EquipDefinition d = equipDefs.get(id);
        if (d == null) throw new IllegalArgumentException("missing EquipDefinition: " + id);
        return d;
    }

    public GameRules gameRules() { return gameRules; }
    public RewardTableConfig rewardTable() { return rewardTableConfig; }
    public EncounterTableConfig encounterTable() { return encounterTableConfig; }
    public RunConfig runConfig() { return runConfig; }
    public boolean resolvingReaction() { return resolvingReaction; }

    public EngineContext withResolvingReaction(boolean value) {
        if (resolvingReaction == value) return this;
        return new EngineContext(definitions, effects, statusDefs, statusEffects, keywordDefs, keywordEffects,
                passiveDefs, passiveEffects, cardModifierDefs, cardModifierEffects, itemDefs, itemEffects,
                equipDefs, gameRules, rewardTableConfig, encounterTableConfig, runConfig, value);
    }
}
