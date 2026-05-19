package com.example.dueltower.engine.model;

import com.example.dueltower.engine.config.RunConfig;
import com.example.dueltower.engine.config.RunConfigs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class RunState {

    public enum LoopStatus {
        CHOOSE_NODE,
        RESOLVE_NODE,
        SHOW_RESULTS
    }

    public enum NodePhase {
        JUDGEMENT,
        COMBAT,
        EVENT
    }

    public enum Danger {
        LOW,
        MID,
        HIGH
    }

    public record NodeChoice(
            String id,
            String name,
            String typeLabel,
            String rule,
            NodePhase phase,
            Danger danger,
            boolean disabled,
            String disabledReason
    ) {}

    public record CurrentNode(
            String id,
            String name,
            String typeLabel,
            NodePhase phase,
            Danger danger,
            int floor
    ) {}

    public record RecentResult(
            String id,
            String type,
            String title,
            String summary,
            String detail,
            String source,
            String at
    ) {}

    public record InventoryEntry(
            String inventoryEquipId,
            InventoryEntryRef ref,
            int count,
            boolean bound,
            Integer loadedAmmo,
            Integer maxLoadedAmmo
    ) {
        public InventoryEntry {
            count = Math.max(0, count);
            if (ref instanceof EquipRef) {
                if (inventoryEquipId == null || inventoryEquipId.isBlank()) {
                    throw new IllegalArgumentException("inventoryEquipId is required for equip entry");
                }
                count = 1;
            } else {
                inventoryEquipId = null;
                loadedAmmo = null;
                maxLoadedAmmo = null;
            }
        }

        public static InventoryEntry item(ItemRef ref, int count, boolean bound) {
            return new InventoryEntry(null, ref, count, bound, null, null);
        }

        public static InventoryEntry equip(String inventoryEquipId, EquipRef ref, boolean bound, Integer loadedAmmo, Integer maxLoadedAmmo) {
            return new InventoryEntry(inventoryEquipId, ref, 1, bound, loadedAmmo, maxLoadedAmmo);
        }
    }

    public record ShopOffer(
            String offerId,
            InventoryEntryRef ref,
            int price,
            int stock,
            boolean bound
    ) {}

    public record ShopOfferState(
            String offerId,
            InventoryEntryRef ref,
            int price,
            int stockRemaining,
            boolean bound
    ) {
        public ShopOfferState {
            stockRemaining = Math.max(0, stockRemaining);
        }
    }

    public static final class Inventory {
        private int keys;
        private int chests;
        private int gold;
        private final List<InventoryEntry> items = new ArrayList<>();

        public int keys() { return keys; }
        public void keys(int keys) { this.keys = Math.max(0, keys); }

        public int chests() { return chests; }
        public void chests(int chests) { this.chests = Math.max(0, chests); }

        public int gold() { return gold; }
        public void gold(int gold) { this.gold = Math.max(0, gold); }

        public List<InventoryEntry> items() { return Collections.unmodifiableList(items); }

        public void replaceItems(List<InventoryEntry> value) {
            items.clear();
            if (value != null) {
                items.addAll(value);
            }
        }
    }

    public static final class ShopState {
        private boolean open;
        private final List<ShopOfferState> offers = new ArrayList<>();

        public boolean open() { return open; }
        public void open(boolean open) { this.open = open; }

        public List<ShopOfferState> offers() {
            return Collections.unmodifiableList(offers);
        }

        public void replaceOffers(List<ShopOfferState> value) {
            offers.clear();
            if (value != null) {
                offers.addAll(value);
            }
        }

        public void replaceOffer(ShopOfferState value) {
            if (value == null) {
                return;
            }
            for (int i = 0; i < offers.size(); i++) {
                if (offers.get(i).offerId().equals(value.offerId())) {
                    offers.set(i, value);
                    return;
                }
            }
        }
    }

    private final RunConfig runConfig;
    private int floor = 1;
    private boolean currentFloorCleared;
    private CurrentNode currentNode;
    private boolean resultPending;
    private final List<NodeChoice> availableChoices = new ArrayList<>();
    private final List<RecentResult> recentResults = new ArrayList<>();
    private final Inventory inventory = new Inventory();
    private final ShopState shopState = new ShopState();

    public RunState() {
        this(RunConfigs.defaultConfig());
    }

    public RunState(RunConfig runConfig) {
        this.runConfig = Objects.requireNonNull(runConfig, "runConfig");
    }

    public RunConfig runConfig() { return runConfig; }

    public int floor() { return floor; }
    public boolean currentFloorCleared() { return currentFloorCleared; }
    public boolean currentFloorSafeZone() { return currentFloorCleared; }
    public boolean canAdvanceToNextFloor() { return currentFloorCleared; }

    public CurrentNode currentNode() { return currentNode; }

    public boolean resultPending() { return resultPending; }

    public LoopStatus status() {
        if (resultPending) {
            return LoopStatus.SHOW_RESULTS;
        }
        if (currentNode != null) {
            return LoopStatus.RESOLVE_NODE;
        }
        return LoopStatus.CHOOSE_NODE;
    }

    public List<NodeChoice> availableChoices() {
        return Collections.unmodifiableList(availableChoices);
    }

    public List<RecentResult> recentResults() {
        return Collections.unmodifiableList(recentResults);
    }

    public Inventory inventory() {
        return inventory;
    }

    public ShopState shopState() {
        return shopState;
    }

    public void initialize(long seed) {
        floor = 1;
        currentFloorCleared = false;
        currentNode = null;
        resultPending = false;
        closeShop();
        inventory.keys(runConfig.startingKeys());
        inventory.chests(runConfig.startingChests());
        inventory.gold(runConfig.startingGold());
        inventory.replaceItems(runConfig.startingItems());
        availableChoices.clear();
        availableChoices.addAll(generateChoices(floor, seed, inventory, runConfig.nodePool()));
    }

    public NodeChoice findChoice(String choiceId) {
        if (choiceId == null || choiceId.isBlank()) {
            return null;
        }
        for (NodeChoice choice : availableChoices) {
            if (Objects.equals(choice.id(), choiceId.trim())) {
                return choice;
            }
        }
        return null;
    }

    public void beginNode(NodeChoice choice) {
        if (choice == null) {
            return;
        }
        this.currentNode = new CurrentNode(
                choice.id(),
                choice.name(),
                choice.typeLabel(),
                choice.phase(),
                choice.danger(),
                floor
        );
        this.resultPending = false;
        availableChoices.clear();
        if (choice.phase() == NodePhase.EVENT) {
            openDefaultShop();
        } else {
            closeShop();
        }
    }

    public boolean currentNodeForcedSuccessJudgement() {
        if (currentNode == null || currentNode.phase() != NodePhase.JUDGEMENT) {
            return false;
        }
        RunConfig.RunNodeDefinition definition = findNodeDefinition(currentNode.id());
        return definition != null && definition.forcedSuccessJudgement();
    }

    public RunConfig.RunNodeDefinition currentNodeDefinition() {
        if (currentNode == null) {
            return null;
        }
        return findNodeDefinition(currentNode.id());
    }

    public RunConfig.RunNodeDefinition nodeDefinition(String nodeId) {
        return findNodeDefinition(nodeId);
    }

    public void resolveCurrentNode(String type,
                                   String title,
                                   String summary,
                                   String detail,
                                   int goldDelta,
                                   int keyDelta,
                                   int chestDelta) {
        appendResult(new RecentResult(
                "result-" + UUID.randomUUID(),
                type,
                title,
                summary,
                detail,
                currentNode == null ? "" : currentNode.name(),
                Instant.now().toString()
        ));
        inventory.gold(inventory.gold() + goldDelta);
        inventory.keys(inventory.keys() + keyDelta);
        inventory.chests(inventory.chests() + chestDelta);
        resultPending = true;
    }

    public void clearRecentResults() {
        recentResults.clear();
        if (resultPending) {
            currentNode = null;
            resultPending = false;
            closeShop();
        }
    }

    public void completeResultAndPrepareNext(long seed) {
        if (resultPending && currentNode != null && canAdvanceToNextFloor()) {
            floor = Math.max(floor, currentNode.floor() + 1);
            currentFloorCleared = false;
        }
        clearRecentResults();
        if (status() == LoopStatus.CHOOSE_NODE && availableChoices.isEmpty()) {
            availableChoices.addAll(generateChoices(floor, seed, inventory, runConfig.nodePool()));
        }
    }

    public boolean markCurrentFloorClearedByBoss() {
        if (currentFloorCleared) {
            return false;
        }
        currentFloorCleared = true;
        return true;
    }

    public ShopOfferState findShopOffer(String offerId) {
        if (offerId == null || offerId.isBlank()) {
            return null;
        }
        for (ShopOfferState offer : shopState.offers()) {
            if (offer.offerId().equals(offerId.trim())) {
                return offer;
            }
        }
        return null;
    }

    public boolean decrementShopOfferStock(String offerId, int count) {
        ShopOfferState offer = findShopOffer(offerId);
        if (offer == null || count <= 0 || count > offer.stockRemaining()) {
            return false;
        }
        shopState.replaceOffer(new ShopOfferState(
                offer.offerId(),
                offer.ref(),
                offer.price(),
                offer.stockRemaining() - count,
                offer.bound()
        ));
        return true;
    }

    public void closeShop() {
        shopState.open(false);
        shopState.replaceOffers(List.of());
    }

    private void openDefaultShop() {
        // Extension point: node-specific shop types can replace this default offer copy later.
        shopState.open(true);
        shopState.replaceOffers(runConfig.defaultShopOffers().stream()
                .map(offer -> new ShopOfferState(
                        offer.offerId(),
                        offer.ref(),
                        offer.price(),
                        offer.stock(),
                        offer.bound()
                ))
                .toList());
    }

    public void appendRecentResult(String type,
                                   String title,
                                   String summary,
                                   String detail,
                                   String source) {
        appendResult(new RecentResult(
                "result-" + UUID.randomUUID(),
                type,
                title,
                summary,
                detail,
                (source == null) ? "" : source,
                Instant.now().toString()
        ));
    }

    private void appendResult(RecentResult result) {
        recentResults.add(0, result);
        if (recentResults.size() > 20) {
            recentResults.subList(20, recentResults.size()).clear();
        }
    }

    private static List<NodeChoice> generateChoices(int floor,
                                                    long seed,
                                                    Inventory inventory,
                                                    List<RunConfig.RunNodeDefinition> nodePool) {
        Random random = new Random(seed ^ ((long) floor * 9973L));
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < nodePool.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt(i -> random.nextInt()));

        Set<String> usedTypes = new LinkedHashSet<>();
        List<NodeChoice> selected = new ArrayList<>();
        for (Integer index : order) {
            RunConfig.RunNodeDefinition node = nodePool.get(index);
            if (usedTypes.contains(node.typeLabel())) {
                continue;
            }
            boolean disabled = node.requiresKey() && inventory.keys() <= 0;
            selected.add(new NodeChoice(
                    node.id(),
                    node.name(),
                    node.typeLabel(),
                    node.rule(),
                    node.phase(),
                    node.danger(),
                    disabled,
                    disabled ? node.keyRequiredReason() : null
            ));
            usedTypes.add(node.typeLabel());
            if (selected.size() >= 3) {
                break;
            }
        }
        return selected;
    }

    private RunConfig.RunNodeDefinition findNodeDefinition(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return null;
        }
        for (RunConfig.RunNodeDefinition node : runConfig.nodePool()) {
            if (node.id().equals(nodeId)) {
                return node;
            }
        }
        return null;
    }
}
