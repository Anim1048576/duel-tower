package com.example.dueltower.content.card.service;

import com.example.dueltower.content.card.dto.CardDetailResponse;
import com.example.dueltower.content.card.dto.CardSummaryResponse;
import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.support.ContentLookupSupport;
import com.example.dueltower.engine.core.effect.card.CardEffect;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids.CardDefId;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;


@Service
public class CardService {
    private final List<CardBlueprint> allBlueprints;
    private final Map<CardDefId, CardBlueprint> blueprintsById;
    private final Map<CardDefId, Integer> maxDeckCopiesById;

    public CardService(List<CardBlueprint> blueprints) {
        // Spring 주입 순서는 보장되지 않으니, 항상 정렬해서 노출
        List<CardBlueprint> sorted = blueprints.stream()
                .sorted(Comparator.comparing(CardBlueprint::id))
                .toList();

        Map<CardDefId, CardBlueprint> bpById = new HashMap<>();
        Map<CardDefId, Integer> deckLimits = new HashMap<>();
        Set<CardDefId> definitionIds = new HashSet<>();

        for (CardBlueprint bp : sorted) {
            CardDefinition def = bp.definition();

            // 안전장치: definition.id == blueprint.id
            if (!def.id().value().equals(bp.id())) {
                throw new IllegalStateException("definition id mismatch: def=" + def.id().value() + ", bp=" + bp.id());
            }

            if (!definitionIds.add(def.id())) {
                throw new IllegalStateException("duplicate card id: " + def.id().value());
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
        }

        validateDefinitionBlueprintConsistency(definitionIds, bpById.keySet());
        this.allBlueprints = List.copyOf(sorted);
        this.blueprintsById = Map.copyOf(bpById);
        this.maxDeckCopiesById = Map.copyOf(deckLimits);
    }

    /** API 용: 전체 목록 */
    public List<CardDefinition> list() {
        return allBlueprints.stream()
                .map(CardBlueprint::definition)
                .toList();
    }

    /** API 용: 타입별 목록 */
    public List<CardDefinition> list(CardType type) {
        return listFiltered(type, null, null);
    }


    /** API 용: 카드 상세 */
    public CardDetailResponse get(String id) {
        CardBlueprint blueprint = requireBlueprint(id);
        CardDefinition definition = blueprint.definition();
        return CardDetailResponse.of(definition, blueprint.contentOwner(), blueprint.playSpec());
    }

    /**
     * 다음 Step 확장 포인트: 카드 검색형 GET은 이 필터를 확장해서 붙인다.
     */
    public List<CardDefinition> listFiltered(CardType type, String q, String keywordId) {
        String query = ContentLookupSupport.normalizeId(q).toLowerCase(Locale.ROOT);
        String normalizedKeywordId = ContentLookupSupport.normalizeId(keywordId);

        return allBlueprints.stream()
                .map(CardBlueprint::definition)
                .filter(card -> matchesFilter(card, type, query, normalizedKeywordId))
                .toList();
    }

    public List<CardSummaryResponse> listFilteredForApi(CardType type, String q, String keywordId) {
        String query = ContentLookupSupport.normalizeId(q).toLowerCase(Locale.ROOT);
        String normalizedKeywordId = ContentLookupSupport.normalizeId(keywordId);

        return allBlueprints.stream()
                .filter(bp -> matchesFilter(bp.definition(), type, query, normalizedKeywordId))
                .map(bp -> CardSummaryResponse.of(bp.definition(), bp.contentOwner()))
                .toList();
    }

    /** 엔진 구성/검증/디버깅용 */
    public Map<CardDefId, CardDefinition> asMap() {
        Map<CardDefId, CardDefinition> definitions = new HashMap<>();
        for (Map.Entry<CardDefId, CardBlueprint> entry : blueprintsById.entrySet()) {
            definitions.put(entry.getKey(), entry.getValue().definition());
        }
        return Map.copyOf(definitions);
    }

    /** 엔진용: 카드 ID -> 실제 효과 구현체(CardBlueprint) */
    public Map<CardDefId, CardEffect> effectsMap() {
        Map<CardDefId, CardEffect> effects = new HashMap<>();
        for (Map.Entry<CardDefId, CardBlueprint> entry : blueprintsById.entrySet()) {
            effects.put(entry.getKey(), entry.getValue());
        }
        return Map.copyOf(effects);
    }

    /** 덱 구성 시 카드별 허용 최대 매수. 오버라이드가 없으면 null */
    public Integer maxDeckCopies(CardDefId id) {
        return maxDeckCopiesById.get(id);
    }

    public CardPlaySpec playSpec(CardDefId id) {
        return requireBlueprint(id).playSpec();
    }

    public boolean exists(CardDefId id) {
        return id != null && id.value() != null && blueprintsById.containsKey(id);
    }

    private CardBlueprint requireBlueprint(CardDefId id) {
        CardBlueprint blueprint = blueprintsById.get(id);
        if (blueprint == null) {
            throw new IllegalStateException("Missing CardBlueprint for card id: " + id.value());
        }
        return blueprint;
    }

    private CardBlueprint requireBlueprint(String rawId) {
        String normalized = ContentLookupSupport.normalizeId(rawId);
        CardDefId id = new CardDefId(normalized);
        CardBlueprint blueprint = blueprintsById.get(id);
        if (blueprint == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "card not found: " + normalized);
        }
        return blueprint;
    }

    private boolean matchesFilter(CardDefinition card, CardType type, String query, String normalizedKeywordId) {
        return (type == null || card.type() == type)
                && (query.isEmpty() || card.name().toLowerCase(Locale.ROOT).contains(query)
                || card.description().toLowerCase(Locale.ROOT).contains(query)
                || card.id().value().toLowerCase(Locale.ROOT).contains(query))
                && (normalizedKeywordId.isEmpty() || card.keywords().containsKey(normalizedKeywordId));
    }

    private void validateDefinitionBlueprintConsistency(
            Set<CardDefId> definitionIds,
            Set<CardDefId> blueprintIds
    ) {
        Set<CardDefId> missingBlueprints = new HashSet<>(definitionIds);
        missingBlueprints.removeAll(blueprintIds);

        Set<CardDefId> orphanBlueprints = new HashSet<>(blueprintIds);
        orphanBlueprints.removeAll(definitionIds);

        if (!missingBlueprints.isEmpty() || !orphanBlueprints.isEmpty()) {
            throw new IllegalStateException(
                    "Card definition/blueprint inconsistency: missingBlueprints=" + missingBlueprints
                            + ", orphanBlueprints=" + orphanBlueprints
            );
        }
    }
}
