package com.example.dueltower.engine.model;

import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.HashMap;
import java.util.Map;

public final class CardInstance {
    private final CardInstId instanceId;
    private final CardDefId defId;
    private final PlayerId ownerId;

    private Zone zone;
    private final Map<String, Integer> counters = new HashMap<>();

    public CardInstance(CardInstId instanceId, CardDefId defId, PlayerId ownerId, Zone zone) {
        this.instanceId = instanceId;
        this.defId = defId;
        this.ownerId = ownerId;
        this.zone = zone;
    }

    public CardInstId instanceId() { return instanceId; }
    public CardDefId defId() { return defId; }
    public PlayerId ownerId() { return ownerId; }
    public Zone zone() { return zone; }
    public void zone(Zone z) { this.zone = z; }

    public Map<String, Integer> counters() { return counters; }
}