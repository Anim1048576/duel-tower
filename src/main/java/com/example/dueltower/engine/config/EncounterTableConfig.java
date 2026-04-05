package com.example.dueltower.engine.config;

import com.example.dueltower.engine.model.EnemyState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.RunState;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

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

    public List<EnemyState> instantiateEncounterEnemies(RunState runState) {
        EncounterTemplate template = selectEncounter(runState);
        int floor = resolveFloor(runState);
        int anchorFloor = template.minFloor() == null ? 1 : Math.max(1, template.minFloor());
        int floorDelta = Math.max(0, floor - anchorFloor);

        List<EnemyState> enemies = new ArrayList<>();
        Set<String> usedEnemyIds = new LinkedHashSet<>();
        for (EnemyTemplate enemyTemplate : template.enemies()) {
            String enemyId = enemyTemplate.enemyId();
            if (!usedEnemyIds.add(enemyId)) {
                throw new IllegalStateException("duplicate enemy id in encounter template: " + enemyId);
            }

            EnemyState enemy = new EnemyState(new Ids.EnemyId(enemyId), enemyTemplate.resolveHp(floorDelta));
            enemy.attackPower(enemyTemplate.resolveAttackPower(floorDelta));
            enemy.healPower(enemyTemplate.resolveHealPower(floorDelta));
            enemies.add(enemy);
        }
        return enemies;
    }

    private int resolveFloor(RunState runState) {
        if (runState.currentNode() != null && runState.currentNode().phase() == RunState.NodePhase.COMBAT) {
            return Math.max(1, runState.currentNode().floor());
        }
        return Math.max(1, runState.floor());
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
                normalizeRequired(raw.enemyId(), "enemyId"),
                requireInt(raw.maxHp(), "maxHp"),
                requireInt(raw.hpPerFloor(), "hpPerFloor"),
                requireInt(raw.attackPower(), "attackPower"),
                requireInt(raw.attackPowerPerFloor(), "attackPowerPerFloor"),
                requireInt(raw.healingPower(), "healingPower"),
                requireInt(raw.healingPowerPerFloor(), "healingPowerPerFloor")
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

    private static int requireInt(Integer value, String fieldName) {
        if (value == null) {
            throw new IllegalStateException("encounter enemy " + fieldName + " is required");
        }
        return value;
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
            String enemyId,
            int baseHp,
            int hpPerFloor,
            int baseAttackPower,
            int attackPowerPerFloor,
            int baseHealPower,
            int healPowerPerFloor
    ) {
        public EnemyTemplate {
            enemyId = Objects.requireNonNull(enemyId, "enemyId");
            if (baseHp <= 0) {
                throw new IllegalArgumentException("baseHp must be > 0");
            }
        }

        int resolveHp(int floorDelta) {
            return Math.max(1, baseHp + (hpPerFloor * floorDelta));
        }

        int resolveAttackPower(int floorDelta) {
            return Math.max(0, baseAttackPower + (attackPowerPerFloor * floorDelta));
        }

        int resolveHealPower(int floorDelta) {
            return Math.max(0, baseHealPower + (healPowerPerFloor * floorDelta));
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
            String enemyId,
            Integer maxHp,
            Integer hpPerFloor,
            Integer attackPower,
            Integer attackPowerPerFloor,
            Integer healingPower,
            Integer healingPowerPerFloor
    ) {}
}
