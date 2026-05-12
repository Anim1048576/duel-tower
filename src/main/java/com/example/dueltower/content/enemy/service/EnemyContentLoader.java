package com.example.dueltower.content.enemy.service;

import com.example.dueltower.content.enemy.model.EnemyContentRaw;
import com.example.dueltower.content.enemy.model.EnemyDefinition;
import com.example.dueltower.content.enemy.model.EnemyPassiveRef;
import com.example.dueltower.content.enemy.model.EnemyRole;
import com.example.dueltower.content.enemy.model.EnemyStatsDefinition;
import com.example.dueltower.content.enemy.model.EnemyStatusRef;
import com.example.dueltower.engine.model.Ids.CardDefId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@Component
public class EnemyContentLoader {
    private static final ObjectMapper JSON = new ObjectMapper();

    private final Resource enemyResource;

    public EnemyContentLoader(@Value("${duel.balance.enemies:classpath:balance/enemies.json}") Resource enemyResource) {
        this.enemyResource = enemyResource;
    }

    public List<EnemyDefinition> loadAll() {
        if (enemyResource == null) {
            throw new IllegalStateException("enemy content resource is missing");
        }
        try (InputStream in = enemyResource.getInputStream()) {
            EnemyContentRaw raw = JSON.readValue(in, EnemyContentRaw.class);
            return fromRaw(raw);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load enemy content from " + enemyResource.getDescription(), e);
        }
    }

    private static List<EnemyDefinition> fromRaw(EnemyContentRaw raw) {
        if (raw == null || raw.enemies() == null || raw.enemies().isEmpty()) {
            throw new IllegalStateException("enemy content must not be empty");
        }
        return raw.enemies().stream()
                .map(EnemyContentLoader::toDefinition)
                .toList();
    }

    private static EnemyDefinition toDefinition(EnemyContentRaw.EnemyRaw raw) {
        if (raw == null) {
            throw new IllegalStateException("enemy definition entry is missing");
        }

        String enemyId = normalizeRequired(raw.id(), "id", "<unknown>");
        EnemyStatsDefinition stats = toStats(raw.stats(), enemyId);
        return new EnemyDefinition(
                enemyId,
                normalizeRequired(raw.name(), "name", enemyId),
                parseRole(raw.role(), enemyId),
                raw.description() == null ? "" : raw.description(),
                stats,
                nullToEmpty(raw.deck()).stream()
                        .map(id -> new CardDefId(normalizeRequired(id, "deck", enemyId)))
                        .toList(),
                nullToEmpty(raw.startStatuses()).stream()
                        .map(status -> toStatusRef(status, enemyId))
                        .toList(),
                nullToEmpty(raw.passives()).stream()
                        .map(passive -> toPassiveRef(passive, enemyId))
                        .toList()
        );
    }

    private static EnemyStatsDefinition toStats(EnemyContentRaw.EnemyStatsRaw raw, String enemyId) {
        if (raw == null) {
            throw new IllegalStateException("enemy stats is required: enemyId=" + enemyId);
        }
        return new EnemyStatsDefinition(
                requireInt(raw.maxHp(), "maxHp", enemyId),
                requireInt(raw.maxActionPoint(), "maxActionPoint", enemyId),
                requireInt(raw.attackPower(), "attackPower", enemyId),
                requireInt(raw.healPower(), "healPower", enemyId)
        );
    }

    private static EnemyStatusRef toStatusRef(EnemyContentRaw.EnemyStatusRaw raw, String enemyId) {
        if (raw == null) {
            throw new IllegalStateException("enemy start status entry is missing: enemyId=" + enemyId);
        }
        return new EnemyStatusRef(
                normalizeRequired(raw.statusId(), "statusId", enemyId),
                requireInt(raw.stacks(), "stacks", enemyId)
        );
    }

    private static EnemyPassiveRef toPassiveRef(EnemyContentRaw.EnemyPassiveRaw raw, String enemyId) {
        if (raw == null) {
            throw new IllegalStateException("enemy passive entry is missing: enemyId=" + enemyId);
        }
        return new EnemyPassiveRef(normalizeRequired(raw.passiveId(), "passiveId", enemyId));
    }

    private static EnemyRole parseRole(String rawRole, String enemyId) {
        String role = normalizeRequired(rawRole, "role", enemyId);
        try {
            return EnemyRole.valueOf(role);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("invalid enemy role: enemyId=" + enemyId + ", role=" + rawRole, ex);
        }
    }

    private static int requireInt(Integer value, String fieldName, String enemyId) {
        if (value == null) {
            throw new IllegalStateException("enemy field is required: enemyId=" + enemyId + ", field=" + fieldName);
        }
        return value;
    }

    private static String normalizeRequired(String value, String fieldName, String enemyId) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("enemy field must not be blank: enemyId=" + enemyId + ", field=" + fieldName);
        }
        return value.trim();
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }
}
