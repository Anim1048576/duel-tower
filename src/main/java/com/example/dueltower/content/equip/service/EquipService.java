package com.example.dueltower.content.equip.service;

import com.example.dueltower.content.equip.model.EquipBlueprint;
import com.example.dueltower.content.support.ContentLookupSupport;
import com.example.dueltower.engine.model.EquipDefinition;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EquipService {
    private final List<EquipDefinition> all;
    private final Map<String, EquipDefinition> defsById;

    public EquipService(List<EquipBlueprint> blueprints) {
        List<EquipBlueprint> sorted = blueprints.stream()
                .sorted(Comparator.comparing(EquipBlueprint::id))
                .toList();

        Map<String, EquipDefinition> d = new HashMap<>();
        List<EquipDefinition> list = new ArrayList<>();

        for (EquipBlueprint bp : sorted) {
            EquipDefinition def = bp.definition();

            if (!def.id().equals(bp.id())) {
                throw new IllegalStateException("equip id mismatch: def=" + def.id() + ", bp=" + bp.id());
            }
            if (d.put(def.id(), def) != null) {
                throw new IllegalStateException("duplicate equip id: " + def.id());
            }
            list.add(def);
        }

        this.all = List.copyOf(list);
        this.defsById = Map.copyOf(d);
    }

    public List<EquipDefinition> list() { return all; }

    public EquipDefinition get(String id) {
        return ContentLookupSupport.requireById(defsById, id, value -> value, "equip");
    }

    public Map<String, EquipDefinition> defsMap() { return defsById; }
}
