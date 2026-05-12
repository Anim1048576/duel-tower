package com.example.dueltower.engine.model;

import com.example.dueltower.config.GameRules;
import com.example.dueltower.content.card.model.OwnedCard;

import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SummonInstId;

import java.util.*;

public final class PlayerState {
    private static final GameRules DEFAULT_GAME_RULES = GameRules.defaults();
    private static final int MIN_LIFE_STAT = 1;

    private final PlayerId playerId;

    // ===== 카드 존 =====
    private final Deque<CardInstId> deck = new ArrayDeque<>();
    private final List<CardInstId> hand = new ArrayList<>();
    private final List<CardInstId> grave = new ArrayList<>();
    private final List<CardInstId> field = new ArrayList<>();
    private final List<CardInstId> excluded = new ArrayList<>();

    private final List<SummonInstId> activeSummons = new ArrayList<>();
    private final Map<CardInstId, SummonInstId> summonByCard = new LinkedHashMap<>();

    // ===== EX/턴 플래그 =====
    private CardInstId exCard;
    /**
     * EX 쿨다운 종료 라운드(포함). 0이면 쿨다운 없음.
     * 예) 현재 라운드가 3이고 exCooldownUntilRound가 4면, 4라운드 종료까지 사용 불가.
     */
    private int exCooldownUntilRound;
    private boolean exActivatable = true;

    /** 패 교환(내 턴 1회) 사용 여부 */
    private boolean swappedThisTurn;
    /** 이번 턴에 PlayCard로 카드를 사용한 횟수 */
    private int cardsPlayedThisTurn;
    /** 이번 턴에 EX 사용 여부 */
    private boolean usedExThisTurn;

    /** 이번 턴에 집념(턴당 1장) 카드 사용 여부 */
    private boolean usedTenacityThisTurn;
    /** 집념으로 인해 발생한 AP 부채 (턴 종료 AP 회복에서 차감) */
    private int tenacityDebtThisTurn;
    /** 이번 턴 소모품 사용 횟수 */
    private int consumablesUsedThisTurn;
    /** 이번 전투 소모품 사용 횟수 */
    private int consumablesUsedThisCombat;

    private PendingDecision pendingDecision;

    private final Map<EquipSlot, EquippedItem> equippedItems = new EnumMap<>(EquipSlot.class);

    // ===== 생활 스탯(기본) =====
    private int body = MIN_LIFE_STAT;   // 신체
    private int skill = MIN_LIFE_STAT;  // 기술
    private int sense = MIN_LIFE_STAT;  // 감각
    private int will = MIN_LIFE_STAT;   // 의지

    // ===== 전투 스탯(현재값) =====
    private int hp;     // 현재 체력
    private int ap;     // 현재 행동력
    private Integer maxHpOverride;

    // ===== 상태/수치(스택) =====
    // 예: "취약"=2, "보호막"=5, "공격력증가"=3 ...
    private final Map<String, Integer> statusValues = new LinkedHashMap<>();
    private final List<String> passiveIds = new ArrayList<>();
    private final List<OwnedCard> ownedCards = new ArrayList<>();
    private final List<String> deckOwnedCardIds = new ArrayList<>();
    private boolean ready;
    private PlayerControlType controlType = PlayerControlType.HUMAN;
    private PlayerId controllerPlayerId;

    public PlayerState(PlayerId playerId) {
        this.playerId = playerId;
        this.controllerPlayerId = playerId;
        // 기본 생활 스탯은 최소 1로 시작하고, 생성 시 현재 HP/AP를 최대치로 채운다.
        this.hp = maxHp();
        this.ap = maxAp();
    }

    public PlayerId playerId() { return playerId; }

    // ===== 카드 존 =====
    public Deque<CardInstId> deck() { return deck; }
    public List<CardInstId> hand() { return hand; }
    public List<CardInstId> grave() { return grave; }
    public List<CardInstId> field() { return field; }
    public List<CardInstId> excluded() { return excluded; }
    public List<SummonInstId> activeSummons() { return activeSummons; }
    public Map<CardInstId, SummonInstId> summonByCard() { return summonByCard; }

    // ===== EX =====
    public CardInstId exCard() { return exCard; }
    public void exCard(CardInstId id) { this.exCard = id; }

    public int exCooldownUntilRound() { return exCooldownUntilRound; }
    public void exCooldownUntilRound(int v) { this.exCooldownUntilRound = v; }

    public boolean exActivatable() { return exActivatable; }
    public void exActivatable(boolean v) { this.exActivatable = v; }

    public boolean exOnCooldown(int currentRound) {
        return exCooldownUntilRound > 0 && currentRound <= exCooldownUntilRound;
    }

    // ===== 턴 플래그 =====
    public boolean swappedThisTurn() { return swappedThisTurn; }
    public void swappedThisTurn(boolean v) { this.swappedThisTurn = v; }

    public int cardsPlayedThisTurn() { return cardsPlayedThisTurn; }
    public void cardsPlayedThisTurn(int v) { this.cardsPlayedThisTurn = Math.max(0, v); }
    public void incCardsPlayedThisTurn() { this.cardsPlayedThisTurn++; }

    public boolean usedExThisTurn() { return usedExThisTurn; }
    public void usedExThisTurn(boolean v) { this.usedExThisTurn = v; }

    public boolean usedTenacityThisTurn() { return usedTenacityThisTurn; }
    public void usedTenacityThisTurn(boolean v) { this.usedTenacityThisTurn = v; }

    public int tenacityDebtThisTurn() { return tenacityDebtThisTurn; }
    public void tenacityDebtThisTurn(int v) { this.tenacityDebtThisTurn = Math.max(0, v); }

    public int consumablesUsedThisTurn() { return consumablesUsedThisTurn; }
    public void consumablesUsedThisTurn(int v) { this.consumablesUsedThisTurn = Math.max(0, v); }
    public void incConsumablesUsedThisTurn() { this.consumablesUsedThisTurn++; }

    public int consumablesUsedThisCombat() { return consumablesUsedThisCombat; }
    public void consumablesUsedThisCombat(int v) { this.consumablesUsedThisCombat = Math.max(0, v); }
    public void incConsumablesUsedThisCombat() { this.consumablesUsedThisCombat++; }

    public PendingDecision pendingDecision() { return pendingDecision; }
    public void pendingDecision(PendingDecision d) { this.pendingDecision = d; }

    public Map<EquipSlot, EquippedItem> equippedItems() { return Collections.unmodifiableMap(equippedItems); }
    public EquippedItem equippedItem(EquipSlot slot) { return equippedItems.get(slot); }
    public void equipItem(EquipSlot slot, EquippedItem item) {
        if (slot == null || item == null) {
            throw new IllegalArgumentException("slot and item are required");
        }
        equippedItems.put(slot, item);
    }
    public EquippedItem unequipItem(EquipSlot slot) { return equippedItems.remove(slot); }

    // ===== 생활 스탯 =====
    public int body() { return body; }
    public void body(int v) { this.body = clampLifeStat(v); clampVitals(); }

    public int skill() { return skill; }
    public void skill(int v) { this.skill = clampLifeStat(v); clampVitals(); }

    public int sense() { return sense; }
    public void sense(int v) { this.sense = clampLifeStat(v); clampVitals(); }

    public int will() { return will; }
    public void will(int v) { this.will = clampLifeStat(v); clampVitals(); }

    // ===== 전투 현재값 =====
    public int hp() { return hp; }
    public void hp(int v) { this.hp = clamp(v, 0, maxHp()); }

    public int ap() { return ap; }
    public void ap(int v) { this.ap = clamp(v, 0, maxAp()); }

    /** 전투 시작/라운드 시작 등에 쓰기 좋음 */
    public void refillToMax() {
        this.hp = maxHp();
        this.ap = maxAp();
    }

    // ===== 파생 전투 스탯(공식 그대로) =====
    public int maxHp() {
        if (maxHpOverride != null) {
            return maxHpOverride;
        }
        // s = body*5 + skill*3 + 3
        int s = body * 5 + skill * 3 + 3;
        int soft = softCapInt(s, 40);          // ROUNDDOWN(MIN(s,40)+MAX(s-40,0)/2)
        return Math.max(soft, 20);             // max(..., 20)
    }

    public void overrideVitals(int hp, int maxHp) {
        this.maxHpOverride = Math.max(1, maxHp);
        this.hp = clamp(hp, 0, this.maxHpOverride);
    }

    public int maxAp() {
        // ROUNDDOWN(3 + will/6, 0) == 3 + floor(will/6)
        return 3 + Math.floorDiv(will, 6);
    }

    public int attackPower() {
        // s = body + skill + sense/2  (sense/2 때문에 0.5 단위 가능)
        int s2 = 2 * (body + skill) + sense;   // s2 = s * 2
        return softCapHalfScaled(s2, 10);
    }

    public int healPower() {
        // s = sense*2 + skill/2  (skill/2 때문에 0.5 단위 가능)
        int s2 = 4 * sense + skill;            // (sense*2)*2 + skill
        return softCapHalfScaled(s2, 10);
    }

    // ===== 상태 스택 =====
    public Map<String, Integer> statusValues() { return statusValues; }

    /**
     * 캐릭터 패시브 ID 목록.
     * 외부에는 읽기 전용 뷰를 제공한다.
     */
    public List<String> passiveIds() { return Collections.unmodifiableList(passiveIds); }

    /**
     * 패시브를 설정한다.
     * - 최대 2개까지 허용
     * - 중복 ID 불가
     */
    public void passiveIds(Collection<String> value) {
        Objects.requireNonNull(value, "passiveIds is required");

        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String raw : value) {
            if (raw == null || raw.isBlank()) {
                throw new IllegalArgumentException("passiveIds contains blank id");
            }
            normalized.add(raw.trim());
        }

        if (normalized.size() != value.size()) {
            throw new IllegalArgumentException("duplicate passiveIds are not allowed");
        }
        if (normalized.size() > DEFAULT_GAME_RULES.maxPassives()) {
            throw new IllegalArgumentException("passiveIds supports up to " + DEFAULT_GAME_RULES.maxPassives());
        }

        passiveIds.clear();
        passiveIds.addAll(normalized);
    }

    public void addPassiveId(String passiveId) {
        if (passiveId == null || passiveId.isBlank()) {
            throw new IllegalArgumentException("passiveId is blank");
        }
        String normalized = passiveId.trim();
        if (passiveIds.contains(normalized)) {
            throw new IllegalArgumentException("duplicate passiveIds are not allowed");
        }
        if (passiveIds.size() >= DEFAULT_GAME_RULES.maxPassives()) {
            throw new IllegalArgumentException("passiveIds supports up to " + DEFAULT_GAME_RULES.maxPassives());
        }
        passiveIds.add(normalized);
    }


    /**
     * 보유 카드 슬롯 목록(최대 20).
     */
    public List<OwnedCard> ownedCards() { return Collections.unmodifiableList(ownedCards); }

    public List<String> deckOwnedCardIds() { return Collections.unmodifiableList(deckOwnedCardIds); }

    public boolean ready() { return ready; }
    public void ready(boolean value) { this.ready = value; }

    public PlayerControlType controlType() { return controlType; }
    public void controlType(PlayerControlType value) {
        this.controlType = (value == null) ? PlayerControlType.HUMAN : value;
        if (this.controlType == PlayerControlType.HUMAN) {
            this.controllerPlayerId = playerId;
        }
    }

    public PlayerId controllerPlayerId() {
        return controllerPlayerId == null ? playerId : controllerPlayerId;
    }

    public void controllerPlayerId(PlayerId value) {
        this.controllerPlayerId = value == null ? playerId : value;
    }

    public void markGmControlledNpc(PlayerId controllerPlayerId) {
        if (controllerPlayerId == null) {
            throw new IllegalArgumentException("controllerPlayerId is required");
        }
        this.controlType = PlayerControlType.GM_CONTROLLED_NPC;
        this.controllerPlayerId = controllerPlayerId;
    }

    public void deckOwnedCardIds(Collection<String> value) {
        Objects.requireNonNull(value, "deckOwnedCardIds is required");

        deckOwnedCardIds.clear();
        for (String ownedCardId : value) {
            if (ownedCardId == null || ownedCardId.isBlank()) {
                throw new IllegalArgumentException("deckOwnedCardIds contains blank id");
            }
            deckOwnedCardIds.add(ownedCardId.trim());
        }
    }

    public void ownedCards(Collection<OwnedCard> value) {
        Objects.requireNonNull(value, "ownedCards is required");

        ownedCards.clear();
        for (OwnedCard card : value) {
            if (card == null || card.cardId() == null || card.cardId().isBlank()) {
                throw new IllegalArgumentException("ownedCards contains invalid cardId");
            }
            if (card.ownedCardId() == null || card.ownedCardId().isBlank()) {
                throw new IllegalArgumentException("ownedCards contains invalid ownedCardId");
            }
            ownedCards.add(new OwnedCard(card.ownedCardId(), card.cardId(), card.modifiers()));
        }
    }

    public int ownedCardCount() {
        return ownedCards.size();
    }

    public int maxOwnedCardCount() {
        return DEFAULT_GAME_RULES.maxOwnedCards();
    }

    public boolean forgettingRequired() {
        return ownedCardCount() > DEFAULT_GAME_RULES.maxOwnedCards();
    }

    public int status(String key) {
        Integer v = statusValues.get(key);
        return v == null ? 0 : v;
    }

    /** value==0이면 제거 */
    public void statusSet(String key, int value) {
        if (value == 0) statusValues.remove(key);
        else statusValues.put(key, value);
    }

    public void statusAdd(String key, int delta) {
        statusSet(key, status(key) + delta);
    }

    // ===== 제한 =====
    public int handLimit() { return DEFAULT_GAME_RULES.handLimit(); }
    public int fieldLimit() { return DEFAULT_GAME_RULES.fieldLimit(); }

    // ===== 내부 유틸 =====
    private void clampVitals() {
        // 생활 스탯이 바뀌면 maxHp/maxAp도 바뀌니까 현재값을 안전하게 클램프
        this.hp = clamp(this.hp, 0, maxHp());
        this.ap = clamp(this.ap, 0, maxAp());
    }

    private static int softCapInt(int s, int cap) {
        // ROUNDDOWN(MIN(s,cap)+MAX(s-cap,0)/2,0)
        if (s <= cap) return s;
        return cap + Math.floorDiv((s - cap), 2);
    }

    private static int softCapHalfScaled(int s2, int cap) {
        // s2 = s*2 (0.5 단위를 정수로)
        // floor( min(s,cap) + max(s-cap,0)/2 )
        // == floor( (2*min(s2,cap2) + max(s2-cap2,0)) / 4 )
        int cap2 = cap * 2;
        int base2 = Math.min(s2, cap2);
        int over2 = Math.max(s2 - cap2, 0);
        int num = 2 * base2 + over2;           // /4
        return Math.floorDiv(num, 4);
    }

    private static int clampNonNegative(int v) {
        return Math.max(0, v);
    }

    private static int clampLifeStat(int v) {
        return Math.max(MIN_LIFE_STAT, v);
    }

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}
