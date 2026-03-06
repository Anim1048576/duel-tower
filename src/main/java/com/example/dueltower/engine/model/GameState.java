package com.example.dueltower.engine.model;

import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import com.example.dueltower.engine.model.Ids.SummonInstId;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GameState {
    private final SessionId sessionId;
    private long version = 0;
    private final long seed;

    private final Map<PlayerId, PlayerState> players = new LinkedHashMap<>();
    private final Map<Ids.EnemyId, EnemyState> enemies = new LinkedHashMap<>();
    private final Map<CardInstId, CardInstance> cardInstances = new HashMap<>();
    private final Map<SummonInstId, SummonState> summons = new HashMap<>();

    private CombatState combat;
    private NodeState nodeState = NodeState.NON_COMBAT;

    public GameState(SessionId sessionId, long seed) {
        this.sessionId = sessionId;
        this.seed = seed;
    }

    public SessionId sessionId() { return sessionId; }
    public long seed() { return seed; }

    public long version() { return version; }
    public void bumpVersion() { this.version++; }

    public Map<PlayerId, PlayerState> players() { return players; }
    public PlayerState player(PlayerId id) { return players.get(id); }

    public Map<Ids.EnemyId, EnemyState> enemies() { return enemies; }
    public EnemyState enemy(Ids.EnemyId id) { return enemies.get(id); }

    public Map<CardInstId, CardInstance> cardInstances() { return cardInstances; }
    public CardInstance card(CardInstId id) { return cardInstances.get(id); }

    public Map<SummonInstId, SummonState> summons() { return summons; }
    public SummonState summon(SummonInstId id) { return summons.get(id); }

    public SummonState summonBySourceCard(CardInstId cardId) {
        if (cardId == null) return null;
        return summons.values().stream()
                .filter(s -> cardId.equals(s.sourceCardId()))
                .findFirst()
                .orElse(null);
    }

    public CombatState combat() { return combat; }
    public void combat(CombatState c) {
        this.combat = c;
        this.nodeState = (c == null) ? NodeState.NON_COMBAT : NodeState.COMBAT;
    }

    public NodeState nodeState() { return nodeState; }
    public void nodeState(NodeState nodeState) { this.nodeState = nodeState; }
}
