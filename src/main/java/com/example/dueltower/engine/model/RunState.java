package com.example.dueltower.engine.model;

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
            InventoryEntryRef ref,
            int count,
            boolean bound
    ) {}

    public record ShopOffer(
            String offerId,
            InventoryEntryRef ref,
            int price,
            int stock,
            boolean bound
    ) {}

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

    private static final List<NodeChoice> NODE_POOL = List.of(
            new NodeChoice("N-1", "회랑 정찰", "판정", "판정 성공 시 안전한 지름길 발견", NodePhase.JUDGEMENT, Danger.MID, false, null),
            new NodeChoice("N-2", "붕괴 전장", "전투", "적 선공 확률 증가", NodePhase.COMBAT, Danger.HIGH, false, null),
            new NodeChoice("N-3", "폐허 저장고", "이벤트", "보상 카드 1장 획득", NodePhase.EVENT, Danger.LOW, false, null),
            new NodeChoice("N-4", "봉인된 균열", "전투", "열쇠 미보유 시 입장 불가", NodePhase.COMBAT, Danger.HIGH, true, "균열 열쇠가 없어 진입할 수 없음"),
            new NodeChoice("N-5", "안식처", "이벤트", "체력과 행동력을 정비한다", NodePhase.EVENT, Danger.LOW, false, null)
    );

    private static final List<ShopOffer> DEFAULT_SHOP_OFFERS = List.of(
            new ShopOffer("O-1", new ItemRef("I-1"), 50, 5, false),
            new ShopOffer("O-2", new ItemRef("I-2"), 200, 5, false),
            new ShopOffer("O-3", new ItemRef("I-3"), 500, 5, false),
            new ShopOffer("O-4", new ItemRef("I-4"), 50, 5, false),
            new ShopOffer("O-5", new ItemRef("I-5"), 200, 5, false),
            new ShopOffer("O-6", new ItemRef("I-6"), 250, 5, false),
            new ShopOffer("O-7", new ItemRef("I-7"), 500, 5, false),
            new ShopOffer("O-8", new EquipRef("E-1"), 200, 5, false),
            new ShopOffer("O-9", new EquipRef("E-2"), 250, 5, false),
            new ShopOffer("O-10", new ItemRef("I-8"), 25, 5, false)
    );

    private int floor = 1;
    private CurrentNode currentNode;
    private boolean resultPending;
    private final List<NodeChoice> availableChoices = new ArrayList<>();
    private final List<RecentResult> recentResults = new ArrayList<>();
    private final Inventory inventory = new Inventory();

    public int floor() { return floor; }

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

    public void initialize(long seed) {
        floor = 1;
        currentNode = null;
        resultPending = false;
        inventory.keys(2);
        inventory.chests(1);
        inventory.gold(12450);
        inventory.replaceItems(defaultInventoryItems());
        availableChoices.clear();
        availableChoices.addAll(generateChoices(floor, seed, inventory));
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
        }
    }

    public void completeResultAndPrepareNext(long seed) {
        if (resultPending && currentNode != null) {
            floor = Math.max(floor, currentNode.floor() + 1);
        }
        clearRecentResults();
        if (status() == LoopStatus.CHOOSE_NODE && availableChoices.isEmpty()) {
            availableChoices.addAll(generateChoices(floor, seed, inventory));
        }
    }

    public ShopOffer findShopOffer(String offerId) {
        if (offerId == null || offerId.isBlank()) {
            return null;
        }
        for (ShopOffer offer : DEFAULT_SHOP_OFFERS) {
            if (offer.offerId().equals(offerId.trim())) {
                return offer;
            }
        }
        return null;
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

    private static List<NodeChoice> generateChoices(int floor, long seed, Inventory inventory) {
        Random random = new Random(seed ^ ((long) floor * 9973L));
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < NODE_POOL.size(); i++) {
            order.add(i);
        }
        order.sort(Comparator.comparingInt(i -> random.nextInt()));

        Set<String> usedTypes = new LinkedHashSet<>();
        List<NodeChoice> selected = new ArrayList<>();
        for (Integer index : order) {
            NodeChoice base = NODE_POOL.get(index);
            if (usedTypes.contains(base.typeLabel())) {
                continue;
            }
            boolean disabled = base.id().equals("N-4") && inventory.keys() <= 0;
            selected.add(new NodeChoice(
                    base.id(),
                    base.name(),
                    base.typeLabel(),
                    base.rule(),
                    base.phase(),
                    base.danger(),
                    disabled,
                    disabled ? "균열 열쇠가 없어 진입할 수 없음" : null
            ));
            usedTypes.add(base.typeLabel());
            if (selected.size() >= 3) {
                break;
            }
        }
        return selected;
    }

    private static List<InventoryEntry> defaultInventoryItems() {
        return List.of(
                new InventoryEntry(new ItemRef("I-1"), 3, false),
                new InventoryEntry(new ItemRef("I-2"), 1, false),
                new InventoryEntry(new ItemRef("I-4"), 1, false),
                new InventoryEntry(new ItemRef("I-6"), 1, false)
        );
    }
}
