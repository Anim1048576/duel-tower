package com.example.dueltower.engine.config;

import com.example.dueltower.engine.model.RunState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record EncounterTableConfig(
        List<EncounterTemplate> encounters,
        String fallbackEncounterId
) {

    public EncounterTableConfig {
        encounters = List.copyOf(Objects.requireNonNull(encounters, "encounters"));
        if (encounters.isEmpty()) {
            throw new IllegalStateException("encounters config must not be empty");
        }

        fallbackEncounterId = normalizeRequired(fallbackEncounterId, "fallbackEncounterId");
        String normalizedFallbackEncounterId = fallbackEncounterId;
        boolean hasFallback = encounters.stream().anyMatch(template -> template.encounterId().equals(normalizedFallbackEncounterId));
        if (!hasFallback) {
            throw new IllegalStateException("fallback encounterId is missing in encounters: " + fallbackEncounterId);
        }
    }

    public static EncounterTableConfig defaults() {
        return EncounterTables.defaultConfig();
    }

    public static EncounterTableConfig fromRaw(EncounterTableRaw raw) {
        if (raw == null) {
            throw new IllegalStateException("encounter table config is missing");
        }
        if (raw.encounters() == null || raw.encounters().isEmpty()) {
            throw new IllegalStateException("encounters config must not be empty");
        }

        List<EncounterTemplate> templates = new ArrayList<>(raw.encounters().size());
        for (EncounterTemplateRaw encounterRaw : raw.encounters()) {
            templates.add(toEncounterTemplate(encounterRaw));
        }

        return new EncounterTableConfig(templates, raw.fallbackEncounterId());
    }

    public EncounterTemplate selectEncounter(RunState runState) {
        Objects.requireNonNull(runState, "runState");

        int floor = resolveFloor(runState);
        RunState.NodePhase phase = runState.currentNode() == null ? null : runState.currentNode().phase();

        for (EncounterTemplate template : encounters) {
            if (!template.matches(floor, phase)) {
                continue;
            }
            return template;
        }

        for (EncounterTemplate template : encounters) {
            if (template.encounterId().equals(fallbackEncounterId)) {
                return template;
            }
        }

        throw new IllegalStateException("encounter not found for floor=" + floor + ", phase=" + phase + ", fallback=" + fallbackEncounterId);
    }

    public int resolveFloor(RunState runState) {
        Objects.requireNonNull(runState, "runState");
        if (runState.currentNode() != null && runState.currentNode().phase() == RunState.NodePhase.COMBAT) {
            return Math.max(1, runState.currentNode().floor());
        }
        return Math.max(1, runState.floor());
    }

    public int resolveFloorDelta(RunState runState, EncounterTemplate template) {
        Objects.requireNonNull(template, "template");
        int floor = resolveFloor(runState);
        int anchorFloor = template.minFloor() == null ? 1 : Math.max(1, template.minFloor());
        return Math.max(0, floor - anchorFloor);
    }

    private static EncounterTemplate toEncounterTemplate(EncounterTemplateRaw raw) {
        if (raw == null) {
            throw new IllegalStateException("encounter template config is missing");
        }

        List<EnemyTemplate> enemies = new ArrayList<>();
        if (raw.enemies() != null) {
            for (EnemyTemplateRaw enemyRaw : raw.enemies()) {
                enemies.add(toEnemyTemplate(enemyRaw));
            }
        }

        RunState.NodePhase requiredNodePhase = parseNodePhase(raw.requiredNodePhase());

        return new EncounterTemplate(
                normalizeRequired(raw.encounterId(), "encounterId"),
                raw.minFloor(),
                raw.maxFloor(),
                requiredNodePhase,
                enemies
        );
    }

    private static EnemyTemplate toEnemyTemplate(EnemyTemplateRaw raw) {
        if (raw == null) {
            throw new IllegalStateException("enemy template config is missing");
        }
        return new EnemyTemplate(
                normalizeRequired(raw.enemyDefId(), "enemyDefId"),
                normalizeRequired(raw.instanceId(), "instanceId"),
                nullToZero(raw.hpPerFloor()),
                nullToZero(raw.attackPowerPerFloor()),
                nullToZero(raw.healingPowerPerFloor())
        );
    }

    private static RunState.NodePhase parseNodePhase(String rawPhase) {
        if (rawPhase == null || rawPhase.isBlank()) {
            return null;
        }
        try {
            return RunState.NodePhase.valueOf(rawPhase.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("invalid requiredNodePhase: " + rawPhase, ex);
        }
    }

    private static String normalizeRequired(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("encounter " + fieldName + " must not be blank");
        }
        return raw.trim();
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    public record EncounterTemplate(
            String encounterId,
            Integer minFloor,
            Integer maxFloor,
            RunState.NodePhase requiredNodePhase,
            List<EnemyTemplate> enemies
    ) {
        public EncounterTemplate {
            encounterId = Objects.requireNonNull(encounterId, "encounterId");
            enemies = List.copyOf(Objects.requireNonNull(enemies, "enemies"));
            if (enemies.isEmpty()) {
                throw new IllegalArgumentException("encounter enemies must not be empty");
            }
            LinkedHashSet<String> instanceIds = new LinkedHashSet<>();
            for (EnemyTemplate enemy : enemies) {
                if (!instanceIds.add(enemy.instanceId())) {
                    throw new IllegalArgumentException("duplicate enemy instance id in encounter: encounterId=" + encounterId + ", instanceId=" + enemy.instanceId());
                }
            }
            if (minFloor != null && minFloor < 1) {
                throw new IllegalArgumentException("minFloor must be >= 1");
            }
            if (maxFloor != null && maxFloor < 1) {
                throw new IllegalArgumentException("maxFloor must be >= 1");
            }
            if (minFloor != null && maxFloor != null && minFloor > maxFloor) {
                throw new IllegalArgumentException("minFloor must be <= maxFloor");
            }
        }

        boolean matches(int floor, RunState.NodePhase phase) {
            if (minFloor != null && floor < minFloor) {
                return false;
            }
            if (maxFloor != null && floor > maxFloor) {
                return false;
            }
            if (requiredNodePhase != null && requiredNodePhase != phase) {
                return false;
            }
            return true;
        }
    }

    public record EnemyTemplate(
            String enemyDefId,
            String instanceId,
            int hpPerFloor,
            int attackPowerPerFloor,
            int healingPowerPerFloor
    ) {
        public EnemyTemplate {
            enemyDefId = normalizeRequired(enemyDefId, "enemyDefId");
            instanceId = normalizeRequired(instanceId, "instanceId");
        }
    }

    public record EncounterTableRaw(
            List<EncounterTemplateRaw> encounters,
            String fallbackEncounterId
    ) {}

    public record EncounterTemplateRaw(
            String encounterId,
            Integer minFloor,
            Integer maxFloor,
            String requiredNodePhase,
            List<EnemyTemplateRaw> enemies
    ) {}

    public record EnemyTemplateRaw(
            String enemyDefId,
            String instanceId,
            Integer hpPerFloor,
            Integer attackPowerPerFloor,
            Integer healingPowerPerFloor
    ) {}
}
