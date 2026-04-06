package com.example.dueltower.engine.config;

import com.example.dueltower.engine.model.RunState;

import java.util.ArrayList;
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
        if (startingKeys < 0) {
            throw new IllegalStateException("run startingKeys must be >= 0");
        }
        if (startingChests < 0) {
            throw new IllegalStateException("run startingChests must be >= 0");
        }
        if (startingGold < 0) {
            throw new IllegalStateException("run startingGold must be >= 0");
        }
        startingItems = List.copyOf(Objects.requireNonNull(startingItems, "startingItems"));
        nodePool = List.copyOf(Objects.requireNonNull(nodePool, "nodePool"));
        defaultShopOffers = List.copyOf(Objects.requireNonNull(defaultShopOffers, "defaultShopOffers"));
        if (nodePool.isEmpty()) {
            throw new IllegalStateException("run nodePool must not be empty");
        }
        if (defaultShopOffers.isEmpty()) {
            throw new IllegalStateException("run defaultShopOffers must not be empty");
        }
        for (RunState.InventoryEntry entry : startingItems) {
            if (entry == null) {
                throw new IllegalStateException("run startingItems[] must not be null");
            }
            if (!(entry.ref() instanceof com.example.dueltower.engine.model.ItemRef ref) || ref.itemId().isBlank()) {
                throw new IllegalStateException("run startingItems[] ref must be non-blank item id");
            }
            if (entry.count() <= 0) {
                throw new IllegalStateException("run startingItems[] count must be > 0");
            }
        }
        for (RunState.ShopOffer offer : defaultShopOffers) {
            if (offer == null) {
                throw new IllegalStateException("run defaultShopOffers[] must not be null");
            }
            if (offer.offerId() == null || offer.offerId().isBlank()) {
                throw new IllegalStateException("run defaultShopOffers[] offerId must not be blank");
            }
            if (offer.price() < 0) {
                throw new IllegalStateException("run defaultShopOffers[] price must be >= 0");
            }
            if (offer.stock() < 0) {
                throw new IllegalStateException("run defaultShopOffers[] stock must be >= 0");
            }
        }
    }

    public static RunConfig fromRaw(RunConfigRaw raw) {
        if (raw == null) {
            throw new IllegalStateException("run config is missing");
        }
        List<RunState.InventoryEntry> startingItems = new ArrayList<>();
        if (raw.startingItems() != null) {
            for (StartingItemRaw entry : raw.startingItems()) {
                String itemId = normalizeRequired(entry == null ? null : entry.itemId(), "startingItems[].itemId");
                int count = entry == null ? 0 : entry.count();
                boolean bound = entry != null && entry.bound();
                startingItems.add(RunState.InventoryEntry.item(new com.example.dueltower.engine.model.ItemRef(itemId), count, bound));
            }
        }

        List<RunNodeDefinition> nodePool = new ArrayList<>();
        if (raw.nodePool() != null) {
            for (RunNodeDefinitionRaw node : raw.nodePool()) {
                if (node == null) {
                    throw new IllegalStateException("run nodePool[] must not be null");
                }
                nodePool.add(new RunNodeDefinition(
                        normalizeRequired(node.id(), "nodePool[].id"),
                        normalizeRequired(node.name(), "nodePool[].name"),
                        normalizeRequired(node.typeLabel(), "nodePool[].typeLabel"),
                        normalizeRequired(node.rule(), "nodePool[].rule"),
                        Objects.requireNonNull(node.phase(), "nodePool[].phase"),
                        Objects.requireNonNull(node.danger(), "nodePool[].danger"),
                        node.requiresKey(),
                        node.keyRequiredReason() == null ? null : node.keyRequiredReason().trim(),
                        Boolean.TRUE.equals(node.forcedSuccessJudgement()),
                        NodeType.parse(node.nodeType(), node.typeLabel()),
                        NodeEffect.fromRaw(node.effect()),
                        parseMysteryOutcomes(node.mysteryOutcomes())
                ));
            }
        }

        List<RunState.ShopOffer> defaultShopOffers = new ArrayList<>();
        if (raw.defaultShopOffers() != null) {
            for (ShopOfferRaw offer : raw.defaultShopOffers()) {
                if (offer == null) {
                    throw new IllegalStateException("run defaultShopOffers[] must not be null");
                }
                defaultShopOffers.add(new RunState.ShopOffer(
                        normalizeRequired(offer.offerId(), "defaultShopOffers[].offerId"),
                        toInventoryEntryRef(offer.refId(), "defaultShopOffers[].refId"),
                        offer.price(),
                        offer.stock(),
                        offer.bound()
                ));
            }
        }

        return new RunConfig(
                raw.startingKeys(),
                raw.startingChests(),
                raw.startingGold(),
                startingItems,
                nodePool,
                defaultShopOffers
        );
    }

    private static com.example.dueltower.engine.model.InventoryEntryRef toInventoryEntryRef(String refId, String fieldName) {
        String normalized = normalizeRequired(refId, fieldName);
        if (normalized.startsWith("E-")) {
            return new com.example.dueltower.engine.model.EquipRef(normalized);
        }
        return new com.example.dueltower.engine.model.ItemRef(normalized);
    }

    private static String normalizeRequired(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("run " + fieldName + " must not be blank");
        }
        return raw.trim();
    }

    public record RunNodeDefinition(
            String id,
            String name,
            String typeLabel,
            String rule,
            RunState.NodePhase phase,
            RunState.Danger danger,
            boolean requiresKey,
            String keyRequiredReason,
            boolean forcedSuccessJudgement,
            NodeType nodeType,
            NodeEffect effect,
            List<NodeType> mysteryOutcomes
    ) {
        public RunNodeDefinition {
            id = Objects.requireNonNull(id, "id");
            name = Objects.requireNonNull(name, "name");
            typeLabel = Objects.requireNonNull(typeLabel, "typeLabel");
            rule = Objects.requireNonNull(rule, "rule");
            phase = Objects.requireNonNull(phase, "phase");
            danger = Objects.requireNonNull(danger, "danger");
            nodeType = Objects.requireNonNull(nodeType, "nodeType");
            mysteryOutcomes = mysteryOutcomes == null ? List.of() : List.copyOf(mysteryOutcomes);
        }

        public RunNodeDefinition(
                String id,
                String name,
                String typeLabel,
                String rule,
                RunState.NodePhase phase,
                RunState.Danger danger,
                boolean requiresKey,
                String keyRequiredReason
        ) {
            this(id, name, typeLabel, rule, phase, danger, requiresKey, keyRequiredReason, false,
                    NodeType.parse(null, typeLabel), null, List.of());
        }

        public RunNodeDefinition(
                String id,
                String name,
                String typeLabel,
                String rule,
                RunState.NodePhase phase,
                RunState.Danger danger,
                boolean requiresKey,
                String keyRequiredReason,
                boolean forcedSuccessJudgement
        ) {
            this(id, name, typeLabel, rule, phase, danger, requiresKey, keyRequiredReason, forcedSuccessJudgement,
                    NodeType.parse(null, typeLabel), null, List.of());
        }
    }

    public enum NodeType {
        NORMAL,
        COMBAT,
        BOSS,
        FACILITY,
        CURSE,
        MYSTERY;

        public static NodeType parse(String rawNodeType, String typeLabel) {
            if (rawNodeType != null && !rawNodeType.isBlank()) {
                try {
                    return NodeType.valueOf(rawNodeType.trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    // fallback to type label
                }
            }
            String normalized = typeLabel == null ? "" : typeLabel.trim();
            return switch (normalized) {
                case "전투" -> COMBAT;
                case "보스" -> BOSS;
                case "시설" -> FACILITY;
                case "저주" -> CURSE;
                case "???" -> MYSTERY;
                default -> NORMAL;
            };
        }
    }

    public record NodeEffect(
            int goldDelta,
            int keyDelta,
            int chestDelta,
            int hpDelta,
            String summary,
            String detail
    ) {
        public static NodeEffect fromRaw(NodeEffectRaw raw) {
            if (raw == null) {
                return null;
            }
            return new NodeEffect(
                    raw.goldDelta(),
                    raw.keyDelta(),
                    raw.chestDelta(),
                    raw.hpDelta(),
                    raw.summary(),
                    raw.detail()
            );
        }
    }

    public record RunConfigRaw(
            int startingKeys,
            int startingChests,
            int startingGold,
            List<StartingItemRaw> startingItems,
            List<RunNodeDefinitionRaw> nodePool,
            List<ShopOfferRaw> defaultShopOffers
    ) {}

    public record StartingItemRaw(
            String itemId,
            int count,
            boolean bound
    ) {}

    public record RunNodeDefinitionRaw(
            String id,
            String name,
            String typeLabel,
            String rule,
            RunState.NodePhase phase,
            RunState.Danger danger,
            boolean requiresKey,
            String keyRequiredReason,
            Boolean forcedSuccessJudgement,
            String nodeType,
            NodeEffectRaw effect,
            List<String> mysteryOutcomes
    ) {}

    public record NodeEffectRaw(
            int goldDelta,
            int keyDelta,
            int chestDelta,
            int hpDelta,
            String summary,
            String detail
    ) {}

    public record ShopOfferRaw(
            String offerId,
            String refId,
            int price,
            int stock,
            boolean bound
    ) {}

    private static List<NodeType> parseMysteryOutcomes(List<String> rawOutcomes) {
        if (rawOutcomes == null || rawOutcomes.isEmpty()) {
            return List.of(NodeType.FACILITY, NodeType.CURSE, NodeType.COMBAT);
        }
        List<NodeType> parsed = new ArrayList<>();
        for (String rawOutcome : rawOutcomes) {
            NodeType nodeType = NodeType.parse(rawOutcome, rawOutcome);
            if (nodeType == NodeType.MYSTERY || nodeType == NodeType.NORMAL || nodeType == NodeType.BOSS) {
                continue;
            }
            parsed.add(nodeType);
        }
        if (parsed.isEmpty()) {
            return List.of(NodeType.FACILITY, NodeType.CURSE, NodeType.COMBAT);
        }
        return List.copyOf(parsed);
    }
}
