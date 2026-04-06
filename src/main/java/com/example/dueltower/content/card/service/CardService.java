package com.example.dueltower.content.card.service;

import com.example.dueltower.content.card.dto.CardDetailResponse;
import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.support.ContentLookupSupport;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids.CardDefId;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


@Service
public class CardService {
    private final List<CardDefinition> all;
    private final Map<CardDefId, CardDefinition> byId;
    private final Map<CardDefId, CardBlueprint> blueprintsById;
    private final Map<CardDefId, CardEffect> effectsById;
    private final Map<CardDefId, Integer> maxDeckCopiesById;

    public CardService(List<CardBlueprint> blueprints) {
        // Spring 주입 순서는 보장되지 않으니, 항상 정렬해서 노출
        List<CardBlueprint> sorted = blueprints.stream()
                .sorted(Comparator.comparing(CardBlueprint::id))
                .toList();

        Map<CardDefId, CardDefinition> m = new HashMap<>();
        Map<CardDefId, CardBlueprint> bpById = new HashMap<>();
        Map<CardDefId, CardEffect> e = new HashMap<>();
        Map<CardDefId, Integer> deckLimits = new HashMap<>();
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

            CardBlueprint prevBlueprint = bpById.put(def.id(), bp);
            if (prevBlueprint != null) {
                throw new IllegalStateException("duplicate card blueprint id: " + def.id().value());
            }

            Integer maxDeckCopies = bp.maxDeckCopies();
            if (maxDeckCopies != null) {
                if (maxDeckCopies < 1) {
                    throw new IllegalStateException("invalid maxDeckCopies for " + def.id().value() + ": " + maxDeckCopies);
                }
                deckLimits.put(def.id(), maxDeckCopies);
            }
            defs.add(def);
        }

        validateDefinitionBlueprintConsistency(m, bpById);
        this.all = List.copyOf(defs);
        this.byId = Map.copyOf(m);
        this.blueprintsById = Map.copyOf(bpById);
        this.effectsById = Map.copyOf(e);
        this.maxDeckCopiesById = Map.copyOf(deckLimits);
    }

    /** API 용: 전체 목록 */
    public List<CardDefinition> list() {
        return all;
    }

    /** API 용: 타입별 목록 */
    public List<CardDefinition> list(CardType type) {
        return listFiltered(type, null, null);
    }


    /** API 용: 카드 상세 */
    public CardDetailResponse get(String id) {
        CardDefinition definition = ContentLookupSupport.requireById(byId, id, CardDefId::new, "card");
        CardBlueprint blueprint = requireBlueprint(definition.id());
        return CardDetailResponse.of(definition, blueprint.playSpec());
    }

    /**
     * 다음 Step 확장 포인트: 카드 검색형 GET은 이 필터를 확장해서 붙인다.
     */
    public List<CardDefinition> listFiltered(CardType type, String q, String keywordId) {
        String query = ContentLookupSupport.normalizeId(q).toLowerCase(Locale.ROOT);
        String normalizedKeywordId = ContentLookupSupport.normalizeId(keywordId);

        return all.stream()
                .filter(card -> type == null || card.type() == type)
                .filter(card -> query.isEmpty() || card.name().toLowerCase(Locale.ROOT).contains(query)
                        || card.description().toLowerCase(Locale.ROOT).contains(query)
                        || card.id().value().toLowerCase(Locale.ROOT).contains(query))
                .filter(card -> normalizedKeywordId.isEmpty() || card.keywords().containsKey(normalizedKeywordId))
                .toList();
    }

    /** 엔진 구성/검증/디버깅용 */
    public Map<CardDefId, CardDefinition> asMap() {
        return byId;
    }

    /** 엔진용: 카드 ID -> 실제 효과 구현체(CardBlueprint) */
    public Map<CardDefId, CardEffect> effectsMap() {
        return effectsById;
    }

    /** 덱 구성 시 카드별 허용 최대 매수. 오버라이드가 없으면 null */
    public Integer maxDeckCopies(CardDefId id) {
        return maxDeckCopiesById.get(id);
    }

    public CardPlaySpec playSpec(CardDefId id) {
        return requireBlueprint(id).playSpec();
    }

    private CardBlueprint requireBlueprint(CardDefId id) {
        CardBlueprint blueprint = blueprintsById.get(id);
        if (blueprint == null) {
            throw new IllegalStateException("Missing CardBlueprint for card id: " + id.value());
        }
        return blueprint;
    }

    private void validateDefinitionBlueprintConsistency(
            Map<CardDefId, CardDefinition> definitions,
            Map<CardDefId, CardBlueprint> blueprints
    ) {
        Set<CardDefId> missingBlueprints = new HashSet<>(definitions.keySet());
        missingBlueprints.removeAll(blueprints.keySet());

        Set<CardDefId> orphanBlueprints = new HashSet<>(blueprints.keySet());
        orphanBlueprints.removeAll(definitions.keySet());

        if (!missingBlueprints.isEmpty() || !orphanBlueprints.isEmpty()) {
            throw new IllegalStateException(
                    "Card definition/blueprint inconsistency: missingBlueprints=" + missingBlueprints
                            + ", orphanBlueprints=" + orphanBlueprints
            );
        }
    }
}
