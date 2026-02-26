package com.example.dueltower.content.keyword;

import com.example.dueltower.engine.core.effect.keyword.KeywordEffect;
import com.example.dueltower.engine.model.KeywordDefinition;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class KeywordService {
    private final List<KeywordDefinition> all;
    private final Map<String, KeywordDefinition> defsById;
    private final Map<String, KeywordEffect> effectsById;

    public KeywordService(List<KeywordBlueprint> blueprints) {
        List<KeywordBlueprint> sorted = blueprints.stream()
                .sorted(Comparator.comparing(KeywordBlueprint::id))
                .toList();

        Map<String, KeywordDefinition> d = new HashMap<>();
        Map<String, KeywordEffect> e = new HashMap<>();
        List<KeywordDefinition> list = new ArrayList<>();

        for (KeywordBlueprint bp : sorted) {
            KeywordDefinition def = bp.definition();

            if (!def.id().equals(bp.id())) {
                throw new IllegalStateException("keyword id mismatch: def=" + def.id() + ", bp=" + bp.id());
            }
            if (d.put(def.id(), def) != null) {
                throw new IllegalStateException("duplicate keyword id: " + def.id());
            }
            if (e.put(def.id(), bp) != null) {
                throw new IllegalStateException("duplicate keyword effect id: " + def.id());
            }
            list.add(def);
        }

        this.all = List.copyOf(list);
        this.defsById = Map.copyOf(d);
        this.effectsById = Map.copyOf(e);
    }

    public List<KeywordDefinition> list() { return all; }
    public Map<String, KeywordDefinition> defsMap() { return defsById; }
    public Map<String, KeywordEffect> effectsMap() { return effectsById; }
}
