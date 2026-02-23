package com.example.dueltower.engine.model;

import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class PlayerState {
    private final PlayerId playerId;

    private final Deque<CardInstId> deck = new ArrayDeque<>();
    private final List<CardInstId> hand = new ArrayList<>();
    private final List<CardInstId> grave = new ArrayList<>();
    private final List<CardInstId> field = new ArrayList<>();
    private final List<CardInstId> excluded = new ArrayList<>();

    private CardInstId exCard;
    /**
     * EX 쿨다운 종료 라운드(포함). 0이면 쿨다운 없음.
     * 예) 현재 라운드가 3이고 exCooldownUntilRound가 4면, 4라운드 종료까지 사용 불가.
     */
    private int exCooldownUntilRound;

    /** 패 교환(내 턴 1회) 사용 여부 */
    private boolean swappedThisTurn;

    private PendingDecision pendingDecision;

    public PlayerState(PlayerId playerId) {
        this.playerId = playerId;
    }

    public PlayerId playerId() { return playerId; }

    public Deque<CardInstId> deck() { return deck; }
    public List<CardInstId> hand() { return hand; }
    public List<CardInstId> grave() { return grave; }
    public List<CardInstId> field() { return field; }
    public List<CardInstId> excluded() { return excluded; }

    public CardInstId exCard() { return exCard; }
    public void exCard(CardInstId id) { this.exCard = id; }

    public int exCooldownUntilRound() { return exCooldownUntilRound; }
    public void exCooldownUntilRound(int v) { this.exCooldownUntilRound = v; }

    public boolean exOnCooldown(int currentRound) {
        return exCooldownUntilRound > 0 && currentRound <= exCooldownUntilRound;
    }

    public boolean swappedThisTurn() { return swappedThisTurn; }
    public void swappedThisTurn(boolean v) { this.swappedThisTurn = v; }

    public PendingDecision pendingDecision() { return pendingDecision; }
    public void pendingDecision(PendingDecision d) { this.pendingDecision = d; }

    public int handLimit() { return 6; }
    public int fieldLimit() { return 5; }
}