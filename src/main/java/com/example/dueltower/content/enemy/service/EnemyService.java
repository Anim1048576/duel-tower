package com.example.dueltower.content.enemy.service;

import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.enemy.model.EnemyDefinition;
import com.example.dueltower.content.enemy.model.EnemyPassiveRef;
import com.example.dueltower.content.enemy.model.EnemyStatsDefinition;
import com.example.dueltower.content.enemy.model.EnemyStatusRef;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.content.status.service.StatusService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class EnemyService {
    private final List<EnemyDefinition> all;
    private final Map<String, EnemyDefinition> defsById;

    public EnemyService(
            EnemyContentLoader loader,
            CardService cardService,
            StatusService statusService,
            PassiveService passiveService
    ) {
        List<EnemyDefinition> loaded = loader.loadAll().stream()
                .sorted(Comparator.comparing(EnemyDefinition::id))
                .toList();

        Map<String, EnemyDefinition> byId = new HashMap<>();
        for (EnemyDefinition definition : loaded) {
            validate(definition, cardService, statusService, passiveService);
            EnemyDefinition previous = byId.put(definition.id(), definition);
            if (previous != null) {
                throw new IllegalStateException("duplicate enemy id: " + definition.id());
            }
        }

        this.all = List.copyOf(loaded);
        this.defsById = Map.copyOf(byId);
    }

    public List<EnemyDefinition> list() {
        return all;
    }

    public EnemyDefinition get(String id) {
        String normalized = normalizeId(id);
        EnemyDefinition definition = defsById.get(normalized);
        if (definition == null) {
            throw new IllegalArgumentException("enemy definition not found: " + normalized);
        }
        return definition;
    }

    public boolean exists(String id) {
        return defsById.containsKey(normalizeId(id));
    }

    public Map<String, EnemyDefinition> defsMap() {
        return defsById;
    }

    private static void validate(
            EnemyDefinition definition,
            CardService cardService,
            StatusService statusService,
            PassiveService passiveService
    ) {
        if (definition == null) {
            throw new IllegalStateException("enemy definition is missing");
        }
        requireNotBlank(definition.id(), "id", "<unknown>");
        requireNotBlank(definition.name(), "name", definition.id());
        if (definition.role() == null) {
            throw new IllegalStateException("enemy role is required: enemyId=" + definition.id());
        }
        EnemyStatsDefinition stats = definition.stats();
        if (stats == null) {
            throw new IllegalStateException("enemy stats is required: enemyId=" + definition.id());
        }
        if (stats.maxHp() <= 0) {
            throw new IllegalStateException("invalid enemy stat: enemyId=" + definition.id() + ", field=maxHp, value=" + stats.maxHp());
        }
        if (stats.maxActionPoint() < 0) {
            throw new IllegalStateException("invalid enemy stat: enemyId=" + definition.id() + ", field=maxActionPoint, value=" + stats.maxActionPoint());
        }
        if (stats.attackPower() < 0) {
            throw new IllegalStateException("invalid enemy stat: enemyId=" + definition.id() + ", field=attackPower, value=" + stats.attackPower());
        }
        if (stats.healPower() < 0) {
            throw new IllegalStateException("invalid enemy stat: enemyId=" + definition.id() + ", field=healPower, value=" + stats.healPower());
        }

        definition.deck().forEach(cardId -> {
            if (cardId == null || cardId.value() == null || cardId.value().isBlank()) {
                throw new IllegalStateException("enemy deck card id must not be blank: enemyId=" + definition.id());
            }
            if (!cardService.exists(cardId)) {
                throw new IllegalStateException("missing card referenced by enemy: enemyId=" + definition.id() + ", cardId=" + cardId.value());
            }
        });

        for (EnemyStatusRef status : definition.startStatuses()) {
            if (status == null) {
                throw new IllegalStateException("enemy start status is missing: enemyId=" + definition.id());
            }
            requireNotBlank(status.statusId(), "statusId", definition.id());
            if (status.stacks() <= 0) {
                throw new IllegalStateException("invalid enemy start status stacks: enemyId=" + definition.id() + ", statusId=" + status.statusId() + ", stacks=" + status.stacks());
            }
            if (!statusService.exists(status.statusId())) {
                throw new IllegalStateException("missing status referenced by enemy: enemyId=" + definition.id() + ", statusId=" + status.statusId());
            }
        }

        for (EnemyPassiveRef passive : definition.passives()) {
            if (passive == null) {
                throw new IllegalStateException("enemy passive is missing: enemyId=" + definition.id());
            }
            requireNotBlank(passive.passiveId(), "passiveId", definition.id());
            if (!passiveService.exists(passive.passiveId())) {
                throw new IllegalStateException("missing passive referenced by enemy: enemyId=" + definition.id() + ", passiveId=" + passive.passiveId());
            }
        }
    }

    private static void requireNotBlank(String value, String fieldName, String enemyId) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("enemy field must not be blank: enemyId=" + enemyId + ", field=" + fieldName);
        }
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.trim();
    }
}
