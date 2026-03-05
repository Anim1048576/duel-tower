package com.example.dueltower.content.passive.service;

import com.example.dueltower.content.passive.model.PassiveBlueprint;
import com.example.dueltower.engine.core.effect.passive.PassiveEffect;
import com.example.dueltower.engine.model.PassiveDefinition;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class PassiveService {
    private final List<PassiveDefinition> all;
    private final Map<String, PassiveDefinition> defsById;
    private final Map<String, PassiveEffect> effectsById;

    public PassiveService(List<PassiveBlueprint> blueprints) {
        List<PassiveBlueprint> sorted = blueprints.stream()
                .sorted(Comparator.comparing(PassiveBlueprint::id))
                .toList();

        Map<String, PassiveDefinition> d = new HashMap<>();
        Map<String, PassiveEffect> e = new HashMap<>();
        List<PassiveDefinition> list = new ArrayList<>();

        for (PassiveBlueprint bp : sorted) {
            PassiveDefinition def = bp.definition();

            if (!def.id().equals(bp.id())) {
                throw new IllegalStateException("passive id mismatch: def=" + def.id() + ", bp=" + bp.id());
            }
            if (d.put(def.id(), def) != null) {
                throw new IllegalStateException("duplicate passive id: " + def.id());
            }
            if (e.put(def.id(), bp) != null) {
                throw new IllegalStateException("duplicate passive effect id: " + def.id());
            }
            list.add(def);
        }

        this.all = List.copyOf(list);
        this.defsById = Map.copyOf(d);
        this.effectsById = Map.copyOf(e);
    }

    public List<PassiveDefinition> list() { return all; }
    public Map<String, PassiveDefinition> defsMap() { return defsById; }
    public Map<String, PassiveEffect> effectsMap() { return effectsById; }
}
