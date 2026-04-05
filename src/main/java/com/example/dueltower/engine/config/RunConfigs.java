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
public final class RunConfigs {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String DEFAULT_RESOURCE_PATH = "balance/run-config.json";
    private final RunConfig runConfig;

    @Autowired
    public RunConfigs(@Value("${duel.balance.run-config:classpath:balance/run-config.json}") Resource runConfigResource) {
        this(load(runConfigResource));
    }

    RunConfigs(RunConfig runConfig) {
        this.runConfig = Objects.requireNonNull(runConfig, "runConfig");
    }

    public static RunConfigs defaults() {
        return new RunConfigs(defaultConfig());
    }

    public RunConfig runConfig() {
        return runConfig;
    }

    public static RunConfig defaultConfig() {
        return DefaultHolder.DEFAULT;
    }

    public static RunConfig load(Resource resource) {
        if (resource == null) {
            throw new IllegalStateException("run config resource is missing");
        }
        try (InputStream in = resource.getInputStream()) {
            RunConfig.RunConfigRaw raw = JSON.readValue(in, RunConfig.RunConfigRaw.class);
            return RunConfig.fromRaw(raw);
        } catch (IOException e) {
            throw new IllegalStateException("failed to load run config from " + resource.getDescription(), e);
        }
    }

    private static final class DefaultHolder {
        private static final RunConfig DEFAULT = load(new ClassPathResource(DEFAULT_RESOURCE_PATH));
    }
}
