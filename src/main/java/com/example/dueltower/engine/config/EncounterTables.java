package com.example.dueltower.engine.config;

import com.example.dueltower.engine.model.RunState;

import java.util.List;

public final class EncounterTables {

    private static final EncounterTableConfig DEFAULT = new EncounterTableConfig(
            List.of(
                    new EncounterTableConfig.EncounterTemplate(
                            "RUN-DEFAULT-COMBAT",
                            1,
                            null,
                            RunState.NodePhase.COMBAT,
                            List.of(
                                    new EncounterTableConfig.EnemyTemplate(
                                            "RUN-ENEMY-1",
                                            22,
                                            4,
                                            5,
                                            1,
                                            0,
                                            1
                                    )
                            )
                    )
            ),
            "RUN-DEFAULT-COMBAT"
    );

    private EncounterTables() {
    }

    public static EncounterTableConfig defaultConfig() {
        return DEFAULT;
    }
}
