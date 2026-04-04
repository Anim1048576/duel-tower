package com.example.dueltower.content.item.service;

import com.example.dueltower.content.item.model.ItemBlueprint;
import com.example.dueltower.content.support.ContentLookupSupport;
import com.example.dueltower.engine.core.effect.item.ItemEffect;
import com.example.dueltower.engine.model.ItemDefinition;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
public class ItemService {
    private final List<ItemDefinition> all;
    private final Map<String, ItemDefinition> defsById;
    private final Map<String, ItemEffect> effectsById;

    public ItemService(List<ItemBlueprint> blueprints) {
        List<ItemBlueprint> sorted = blueprints.stream()
                .sorted(Comparator.comparing(ItemBlueprint::id))
                .toList();

        Map<String, ItemDefinition> d = new HashMap<>();
        Map<String, ItemEffect> e = new HashMap<>();
        List<ItemDefinition> list = new ArrayList<>();

        for (ItemBlueprint bp : sorted) {
            ItemDefinition def = bp.definition();

            if (!def.id().equals(bp.id())) {
                throw new IllegalStateException("item id mismatch: def=" + def.id() + ", bp=" + bp.id());
            }
            if (d.put(def.id(), def) != null) {
                throw new IllegalStateException("duplicate item id: " + def.id());
            }
            if (e.put(def.id(), bp) != null) {
                throw new IllegalStateException("duplicate item effect id: " + def.id());
            }
            list.add(def);
        }

        this.all = List.copyOf(list);
        this.defsById = Map.copyOf(d);
        this.effectsById = Map.copyOf(e);
    }

    public List<ItemDefinition> list() { return all; }

    public ItemDefinition get(String id) {
        return ContentLookupSupport.requireById(defsById, id, value -> value, "item");
    }
    public Map<String, ItemDefinition> defsMap() { return defsById; }
    public Map<String, ItemEffect> effectsMap() { return effectsById; }
}
