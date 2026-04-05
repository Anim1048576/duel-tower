package com.example.dueltower.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RewardTableConfigTest {

    @Test
    void sellPriceFallbackIsZeroWhenIdMissing() {
        RewardTableConfig config = new RewardTableConfig(
                RewardTableConfig.defaults().chest(),
                RewardTableConfig.defaults().judgement(),
                Map.of("I-1", 25)
        );
        assertEquals(0, config.sellUnitPrice("UNKNOWN"));
    }

    @Test
    void missingChestConfigThrowsExplicitException() {
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> new RewardTableConfig(
                null,
                RewardTableConfig.defaults().judgement(),
                Map.of()
        ));
        assertEquals("reward chest config is missing", exception.getMessage());
    }
}
