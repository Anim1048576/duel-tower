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
    private boolean exOnCooldown;

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

    public boolean exOnCooldown() { return exOnCooldown; }
    public void exOnCooldown(boolean v) { this.exOnCooldown = v; }

    public PendingDecision pendingDecision() { return pendingDecision; }
    public void pendingDecision(PendingDecision d) { this.pendingDecision = d; }

    public int handLimit() { return 6; }
    public int fieldLimit() { return 5; }
}