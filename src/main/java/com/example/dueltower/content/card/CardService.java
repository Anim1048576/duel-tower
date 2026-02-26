package com.example.dueltower.content.card;

import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.core.effect.CardEffect;
import com.example.dueltower.engine.model.Ids.CardDefId;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CardService {
    private final List<CardDefinition> all;
    private final Map<CardDefId, CardDefinition> byId;
    private final Map<CardDefId, CardEffect> effectsById;

    public CardService(List<CardBlueprint> blueprints) {
        // Spring 주입 순서는 보장되지 않으니, 항상 정렬해서 노출
        List<CardBlueprint> sorted = blueprints.stream()
                .sorted(Comparator.comparing(CardBlueprint::id))
                .toList();

        Map<CardDefId, CardDefinition> m = new HashMap<>();
        Map<CardDefId, CardEffect> e = new HashMap<>();
        List<CardDefinition> defs = new ArrayList<>();

        for (CardBlueprint bp : sorted) {
            CardDefinition def = bp.definition();

            // 안전장치: definition.id == blueprint.id
            if (!def.id().value().equals(bp.id())) {
                throw new IllegalStateException("definition id mismatch: def=" + def.id().value() + ", bp=" + bp.id());
            }

            CardDefinition prev = m.put(def.id(), def);
            if (prev != null) {
                throw new IllegalStateException("duplicate card id: " + def.id().value());
            }

            CardEffect prevEff = e.put(def.id(), bp);
            if (prevEff != null) {
                throw new IllegalStateException("duplicate card effect id: " + def.id().value());
            }
            defs.add(def);
        }

        this.all = List.copyOf(defs);
        this.byId = Map.copyOf(m);
        this.effectsById = Map.copyOf(e);
    }

    /** API 용: 전체 목록 */
    public List<CardDefinition> list() {
        return all;
    }

    /** 엔진 구성/검증/디버깅용 */
    public Map<CardDefId, CardDefinition> asMap() {
        return byId;
    }

    /** 엔진용: 카드 ID -> 실제 효과 구현체(CardBlueprint) */
    public Map<CardDefId, CardEffect> effectsMap() {
        return effectsById;
    }
}