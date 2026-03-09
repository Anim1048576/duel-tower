package com.example.dueltower.engine.model;

import com.example.dueltower.content.card.model.OwnedCardModifier;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Ids.CardInstId;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CardInstance {
    private final CardInstId instanceId;
    private final CardDefId defId;
    private final PlayerId ownerId;
    private final String sourceOwnedCardId;
    private final List<OwnedCardModifier> modifiers;

    private Zone zone;
    private final Map<String, Integer> counters = new HashMap<>();
    private boolean fieldEffectActive;
    private boolean fieldEffectTransitioning;

    public CardInstance(CardInstId instanceId, CardDefId defId, PlayerId ownerId, Zone zone) {
        this(instanceId, defId, ownerId, zone, null, List.of());
    }

    public CardInstance(CardInstId instanceId,
                        CardDefId defId,
                        PlayerId ownerId,
                        Zone zone,
                        String sourceOwnedCardId,
                        List<OwnedCardModifier> modifiers) {
        this.instanceId = instanceId;
        this.defId = defId;
        this.ownerId = ownerId;
        this.zone = zone;
        this.sourceOwnedCardId = normalizeSourceOwnedCardId(sourceOwnedCardId);
        this.modifiers = normalizeModifiers(modifiers);
    }

    public CardInstId instanceId() { return instanceId; }
    public CardDefId defId() { return defId; }
    public PlayerId ownerId() { return ownerId; }
    public Zone zone() { return zone; }
    public void zone(Zone z) { this.zone = z; }
    public String sourceOwnedCardId() { return sourceOwnedCardId; }
    public List<OwnedCardModifier> modifiers() { return modifiers; }

    public boolean hasModifier(String modifierId) {
        if (modifierId == null || modifierId.isBlank()) return false;
        String normalized = modifierId.trim();
        return modifiers.stream().anyMatch(modifier -> modifier.modifierId().equals(normalized));
    }

    public Map<String, Integer> counters() { return counters; }

    public boolean fieldEffectActive() { return fieldEffectActive; }
    public void fieldEffectActive(boolean v) { this.fieldEffectActive = v; }

    public boolean fieldEffectTransitioning() { return fieldEffectTransitioning; }
    public void fieldEffectTransitioning(boolean v) { this.fieldEffectTransitioning = v; }

    private static String normalizeSourceOwnedCardId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static List<OwnedCardModifier> normalizeModifiers(List<OwnedCardModifier> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<OwnedCardModifier> out = new ArrayList<>(source.size());
        for (OwnedCardModifier modifier : source) {
            if (modifier == null) {
                throw new IllegalArgumentException("modifiers contains null");
            }
            out.add(new OwnedCardModifier(modifier.modifierId(), modifier.value()));
        }
        return List.copyOf(out);
    }
}
