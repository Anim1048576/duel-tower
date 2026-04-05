package com.example.dueltower.engine.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

@Component
public final class EncounterTables {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_RESOURCE_PATH = "balance/encounters.json";
    private final EncounterTableConfig encounterTableConfig;

    @Autowired
    public EncounterTables(@Value("${duel.balance.encounters:classpath:balance/encounters.json}") Resource encounterResource) {
        this(load(encounterResource));
    }

    EncounterTables(EncounterTableConfig encounterTableConfig) {
        this.encounterTableConfig = Objects.requireNonNull(encounterTableConfig, "encounterTableConfig");
    }

    public static EncounterTables defaults() {
        return new EncounterTables(defaultConfig());
    }

    public EncounterTableConfig encounterTableConfig() {
        return encounterTableConfig;
    }

    public static EncounterTableConfig defaultConfig() {
        return DefaultHolder.DEFAULT;
    }

    public static EncounterTableConfig load(Resource resource) {
        if (resource == null) {
            throw new IllegalStateException("encounter resource is missing");
        }
        try (InputStream in = resource.getInputStream()) {
            EncounterTableConfig.EncounterTableRaw raw = JSON.readValue(in, EncounterTableConfig.EncounterTableRaw.class);
            return EncounterTableConfig.fromRaw(raw);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load encounter config from " + resource.getDescription(), e);
        }
    }

    private static final class DefaultHolder {
        private static final EncounterTableConfig DEFAULT = load(new ClassPathResource(DEFAULT_RESOURCE_PATH));
    }
}
