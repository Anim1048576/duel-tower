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

    public record InventoryItem(
            String id,
            String name,
            int count,
            boolean bound,
            boolean battleUsable,
            String summary,
            String description,
            List<String> tags
    ) {}

    public record ShopOffer(
            String id,
            int priceGold,
            InventoryItem item
    ) {}

    public static final class Inventory {
        private int keys;
        private int chests;
        private int gold;
        private final List<InventoryItem> items = new ArrayList<>();

        public int keys() { return keys; }
        public void keys(int keys) { this.keys = Math.max(0, keys); }

        public int chests() { return chests; }
        public void chests(int chests) { this.chests = Math.max(0, chests); }

        public int gold() { return gold; }
        public void gold(int gold) { this.gold = Math.max(0, gold); }

        public List<InventoryItem> items() { return Collections.unmodifiableList(items); }

        public void replaceItems(List<InventoryItem> value) {
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
            new ShopOffer(
                    "O-1",
                    180,
                    new InventoryItem("I-1", "소형 회복 물약", 1, false, true, "전투 중 사용 가능 · 체력 20 회복", "즉시 체력을 20 회복합니다. 턴 소모 없이 사용됩니다.", List.of("소모품", "회복"))
            ),
            new ShopOffer(
                    "O-2",
                    320,
                    new InventoryItem("I-4", "긴급 연막탄", 1, true, true, "전투 중 사용 가능 · 회피 상승", "현재 턴 동안 회피율이 크게 상승합니다.", List.of("전투 아이템"))
            )
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
            if (offer.id().equals(offerId.trim())) {
                return offer;
            }
        }
        return null;
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

    private static List<InventoryItem> defaultInventoryItems() {
        return List.of(
                new InventoryItem("I-1", "소형 회복 물약", 8, false, true, "전투 중 사용 가능 · 체력 20 회복", "즉시 체력을 20 회복합니다. 턴 소모 없이 사용됩니다.", List.of("소모품", "회복")),
                new InventoryItem("I-2", "해독제", 3, true, true, "전투 중 사용 가능 · 디버프 해제", "출혈/중독 등 해로운 상태효과 1개를 제거합니다.", List.of("소모품", "정화")),
                new InventoryItem("I-3", "단단한 가죽끈", 12, false, false, "제작 재료", "장비 제작에 사용되는 기본 재료입니다.", List.of("재료")),
                new InventoryItem("I-4", "긴급 연막탄", 2, true, true, "전투 중 사용 가능 · 회피 상승", "현재 턴 동안 회피율이 크게 상승합니다.", List.of("전투 아이템")),
                new InventoryItem("I-5", "강화석 파편", 16, false, false, "강화 재료", "장비 강화 수치에 따라 다량으로 요구됩니다.", List.of("재료"))
        );
    }
}
