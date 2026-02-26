package com.example.dueltower.content.status;

import com.example.dueltower.engine.core.status.StatusEffect;
import com.example.dueltower.engine.model.StatusDefinition;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StatusService {
    private final List<StatusDefinition> all;
    private final Map<String, StatusDefinition> defsById;
    private final Map<String, StatusEffect> effectsById;

    public StatusService(List<StatusBlueprint> blueprints) {
        List<StatusBlueprint> sorted = blueprints.stream()
                .sorted(Comparator.comparing(StatusBlueprint::id))
                .toList();

        Map<String, StatusDefinition> d = new HashMap<>();
        Map<String, StatusEffect> e = new HashMap<>();
        List<StatusDefinition> list = new ArrayList<>();

        for (StatusBlueprint bp : sorted) {
            StatusDefinition def = bp.definition();

            if (!def.id().equals(bp.id())) {
                throw new IllegalStateException("status id mismatch: def=" + def.id() + ", bp=" + bp.id());
            }
            if (d.put(def.id(), def) != null) {
                throw new IllegalStateException("duplicate status id: " + def.id());
            }
            if (e.put(def.id(), bp) != null) {
                throw new IllegalStateException("duplicate status effect id: " + def.id());
            }
            list.add(def);
        }

        this.all = List.copyOf(list);
        this.defsById = Map.copyOf(d);
        this.effectsById = Map.copyOf(e);
    }

    public List<StatusDefinition> list() { return all; }
    public Map<String, StatusDefinition> defsMap() { return defsById; }
    public Map<String, StatusEffect> effectsMap() { return effectsById; }
}
