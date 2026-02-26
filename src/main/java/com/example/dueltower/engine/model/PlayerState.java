package com.example.dueltower.engine.model;

import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.*;

public final class PlayerState {
    private final PlayerId playerId;

    // ===== 카드 존 =====
    private final Deque<CardInstId> deck = new ArrayDeque<>();
    private final List<CardInstId> hand = new ArrayList<>();
    private final List<CardInstId> grave = new ArrayList<>();
    private final List<CardInstId> field = new ArrayList<>();
    private final List<CardInstId> excluded = new ArrayList<>();

    // ===== EX/턴 플래그 =====
    private CardInstId exCard;
    /**
     * EX 쿨다운 종료 라운드(포함). 0이면 쿨다운 없음.
     * 예) 현재 라운드가 3이고 exCooldownUntilRound가 4면, 4라운드 종료까지 사용 불가.
     */
    private int exCooldownUntilRound;

    /** 패 교환(내 턴 1회) 사용 여부 */
    private boolean swappedThisTurn;
    /** 이번 턴에 PlayCard로 카드를 사용한 횟수 */
    private int cardsPlayedThisTurn;
    /** 이번 턴에 EX 사용 여부 */
    private boolean usedExThisTurn;

    private PendingDecision pendingDecision;

    // ===== 생활 스탯(기본) =====
    private int body;   // 신체
    private int skill;  // 기술
    private int sense;  // 감각
    private int will;   // 의지

    // ===== 전투 스탯(현재값) =====
    private int hp;     // 현재 체력
    private int ap;     // 현재 행동력

    // ===== 상태/수치(스택) =====
    // 예: "취약"=2, "보호막"=5, "공격력증가"=3 ...
    private final Map<String, Integer> statusValues = new LinkedHashMap<>();

    public PlayerState(PlayerId playerId) {
        this.playerId = playerId;
        // 기본 스탯 0 기준으로도 maxHp>=20이므로 안전
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

    // ===== EX =====
    public CardInstId exCard() { return exCard; }
    public void exCard(CardInstId id) { this.exCard = id; }

    public int exCooldownUntilRound() { return exCooldownUntilRound; }
    public void exCooldownUntilRound(int v) { this.exCooldownUntilRound = v; }

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

    public PendingDecision pendingDecision() { return pendingDecision; }
    public void pendingDecision(PendingDecision d) { this.pendingDecision = d; }

    // ===== 생활 스탯 =====
    public int body() { return body; }
    public void body(int v) { this.body = clampNonNegative(v); clampVitals(); }

    public int skill() { return skill; }
    public void skill(int v) { this.skill = clampNonNegative(v); clampVitals(); }

    public int sense() { return sense; }
    public void sense(int v) { this.sense = clampNonNegative(v); clampVitals(); }

    public int will() { return will; }
    public void will(int v) { this.will = clampNonNegative(v); clampVitals(); }

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
        // s = body*5 + skill*3 + 3
        int s = body * 5 + skill * 3 + 3;
        int soft = softCapInt(s, 40);          // ROUNDDOWN(MIN(s,40)+MAX(s-40,0)/2)
        return Math.max(soft, 20);             // max(..., 20)
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
    public int handLimit() { return 6; }
    public int fieldLimit() { return 5; }

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

    private static int clamp(int v, int min, int max) {
        if (v < min) return min;
        if (v > max) return max;
        return v;
    }
}