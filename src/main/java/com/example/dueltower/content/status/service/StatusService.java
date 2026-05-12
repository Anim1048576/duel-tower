package com.example.dueltower.content.status.service;

import com.example.dueltower.content.status.dto.StatusResponse;
import com.example.dueltower.content.status.model.StatusBlueprint;
import com.example.dueltower.content.support.ContentLookupSupport;
import com.example.dueltower.engine.core.effect.status.StatusEffect;
import com.example.dueltower.engine.model.StatusDefinition;
import org.springframework.stereotype.Service;

import java.util.*;


@Service
public class StatusService {
    private final List<StatusDefinition> all;
    private final Map<String, StatusDefinition> defsById;
    private final Map<String, StatusEffect> effectsById;
    private final Map<String, String> ownersById;

    public StatusService(List<StatusBlueprint> blueprints) {
        List<StatusBlueprint> sorted = blueprints.stream()
                .sorted(Comparator.comparing(StatusBlueprint::id))
                .toList();

        Map<String, StatusDefinition> d = new HashMap<>();
        Map<String, StatusEffect> e = new HashMap<>();
        Map<String, String> owners = new HashMap<>();
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
            owners.put(def.id(), bp.contentOwner());
            list.add(def);
        }

        this.all = List.copyOf(list);
        this.defsById = Map.copyOf(d);
        this.effectsById = Map.copyOf(e);
        this.ownersById = Map.copyOf(owners);
    }

    public List<StatusDefinition> list() {
        return all.stream()
                .filter(StatusDefinition::publicVisible)
                .toList();
    }

    public List<StatusDefinition> listAll() {
        return all;
    }

    public StatusDefinition get(String id) {
        return ContentLookupSupport.requireById(defsById, id, value -> value, "status");
    }

    public boolean exists(String id) {
        return defsById.containsKey(ContentLookupSupport.normalizeId(id));
    }

    public List<StatusResponse> listForApi() {
        return list().stream()
                .map(this::toResponse)
                .toList();
    }

    public List<StatusResponse> listAllForApi() {
        return listAll().stream()
                .map(this::toResponse)
                .toList();
    }

    public StatusResponse getForApi(String id) {
        return toResponse(get(id));
    }

    private StatusResponse toResponse(StatusDefinition definition) {
        return StatusResponse.of(definition, ownersById.get(definition.id()));
    }

    public Map<String, StatusDefinition> defsMap() { return defsById; }
    public Map<String, StatusEffect> effectsMap() { return effectsById; }
}
