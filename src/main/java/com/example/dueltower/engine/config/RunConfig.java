package com.example.dueltower.engine.config;

import com.example.dueltower.engine.model.RunState;

import java.util.List;
import java.util.Objects;

public record RunConfig(
        int startingKeys,
        int startingChests,
        int startingGold,
        List<RunState.InventoryEntry> startingItems,
        List<RunNodeDefinition> nodePool,
        List<RunState.ShopOffer> defaultShopOffers
) {
    public RunConfig {
        startingItems = List.copyOf(Objects.requireNonNull(startingItems, "startingItems"));
        nodePool = List.copyOf(Objects.requireNonNull(nodePool, "nodePool"));
        defaultShopOffers = List.copyOf(Objects.requireNonNull(defaultShopOffers, "defaultShopOffers"));
    }

    public record RunNodeDefinition(
            String id,
            String name,
            String typeLabel,
            String rule,
            RunState.NodePhase phase,
            RunState.Danger danger,
            boolean requiresKey,
            String keyRequiredReason
    ) {
        public RunNodeDefinition {
            id = Objects.requireNonNull(id, "id");
            name = Objects.requireNonNull(name, "name");
            typeLabel = Objects.requireNonNull(typeLabel, "typeLabel");
            rule = Objects.requireNonNull(rule, "rule");
            phase = Objects.requireNonNull(phase, "phase");
            danger = Objects.requireNonNull(danger, "danger");
        }
    }
}
