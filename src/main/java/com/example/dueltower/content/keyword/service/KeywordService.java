package com.example.dueltower.content.keyword.service;

import com.example.dueltower.content.keyword.dto.KeywordResponse;
import com.example.dueltower.content.keyword.model.KeywordBlueprint;
import com.example.dueltower.content.support.ContentLookupSupport;
import com.example.dueltower.engine.core.effect.keyword.KeywordEffect;
import com.example.dueltower.engine.model.KeywordDefinition;
import com.example.dueltower.engine.model.KeywordRole;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
public class KeywordService {
    private final List<KeywordDefinition> all;
    private final Map<String, KeywordDefinition> defsById;
    private final Map<String, KeywordEffect> effectsById;
    private final Map<String, String> ownersById;

    public KeywordService(List<KeywordBlueprint> blueprints) {
        List<KeywordBlueprint> sorted = blueprints.stream()
                .sorted(Comparator.comparing(KeywordBlueprint::id))
                .toList();

        Map<String, KeywordDefinition> d = new HashMap<>();
        Map<String, KeywordEffect> e = new HashMap<>();
        Map<String, String> owners = new HashMap<>();
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
            owners.put(def.id(), bp.contentOwner());
            list.add(def);
        }

        this.all = List.copyOf(list);
        this.defsById = Map.copyOf(d);
        this.effectsById = Map.copyOf(e);
        this.ownersById = Map.copyOf(owners);
    }

    public List<KeywordDefinition> list() {
        return all.stream()
                .filter(KeywordDefinition::standalone)
                .toList();
    }

    public List<KeywordDefinition> listAll() {
        return all;
    }

    public List<KeywordDefinition> listAttachedTo(String parentKeywordId) {
        if (parentKeywordId == null || parentKeywordId.isBlank()) {
            return List.of();
        }

        return all.stream()
                .filter(def -> def.role() == KeywordRole.ATTACHED)
                .filter(def -> parentKeywordId.equals(def.parentKeywordId()))
                .toList();
    }

    public KeywordDefinition get(String id) {
        return ContentLookupSupport.requireById(defsById, id, value -> value, "keyword");
    }

    public List<KeywordResponse> listForApi() {
        return list().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<KeywordResponse> listAllForApi() {
        return listAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<KeywordResponse> listAttachedToForApi(String parentKeywordId) {
        return listAttachedTo(parentKeywordId).stream()
                .map(this::toResponse)
                .toList();
    }

    public KeywordResponse getForApi(String id) {
        return toResponse(get(id));
    }

    private KeywordResponse toResponse(KeywordDefinition definition) {
        return KeywordResponse.of(definition, ownersById.get(definition.id()));
    }

    public Map<String, KeywordDefinition> defsMap() { return defsById; }
    public Map<String, KeywordEffect> effectsMap() { return effectsById; }
}
