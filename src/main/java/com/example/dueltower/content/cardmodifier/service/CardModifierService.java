package com.example.dueltower.content.cardmodifier.service;

import com.example.dueltower.content.cardmodifier.model.CardModifierBlueprint;
import com.example.dueltower.engine.core.effect.cardmodifier.CardModifierEffect;
import com.example.dueltower.engine.model.CardModifierDefinition;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CardModifierService {
    private final List<CardModifierDefinition> all;
    private final Map<String, CardModifierDefinition> defsById;
    private final Map<String, CardModifierEffect> effectsById;

    public CardModifierService(List<CardModifierBlueprint> blueprints) {
        List<CardModifierBlueprint> sorted = blueprints.stream()
                .sorted(Comparator.comparing(CardModifierBlueprint::id))
                .toList();

        Map<String, CardModifierDefinition> d = new HashMap<>();
        Map<String, CardModifierEffect> e = new HashMap<>();
        List<CardModifierDefinition> list = new ArrayList<>();

        for (CardModifierBlueprint bp : sorted) {
            CardModifierDefinition def = bp.definition();

            if (!def.id().equals(bp.id())) {
                throw new IllegalStateException("card modifier id mismatch: def=" + def.id() + ", bp=" + bp.id());
            }
            if (d.put(def.id(), def) != null) {
                throw new IllegalStateException("duplicate card modifier id: " + def.id());
            }
            if (e.put(def.id(), bp) != null) {
                throw new IllegalStateException("duplicate card modifier effect id: " + def.id());
            }
            list.add(def);
        }

        this.all = List.copyOf(list);
        this.defsById = Map.copyOf(d);
        this.effectsById = Map.copyOf(e);
    }

    public List<CardModifierDefinition> list() { return all; }
    public Map<String, CardModifierDefinition> defsMap() { return defsById; }
    public Map<String, CardModifierEffect> effectsMap() { return effectsById; }
}
