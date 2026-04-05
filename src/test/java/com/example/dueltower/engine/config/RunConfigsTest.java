package com.example.dueltower.engine.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RunConfigsTest {

    @Test
    void loadReadsRunConfigFromClasspathJson() {
        RunConfig config = RunConfigs.load(new ClassPathResource("balance/run-config.json"));

        assertEquals(2, config.startingKeys());
        assertEquals(1, config.startingChests());
        assertEquals(12450, config.startingGold());
        assertEquals(4, config.startingItems().size());
        assertEquals(5, config.nodePool().size());
        assertEquals(10, config.defaultShopOffers().size());
    }

    @Test
    void loadFailsWhenStartingGoldIsNegative() {
        String invalidJson = """
                {
                  \"startingKeys\": 0,
                  \"startingChests\": 0,
                  \"startingGold\": -1,
                  \"startingItems\": [{\"itemId\":\"I-1\",\"count\":1,\"bound\":false}],
                  \"nodePool\": [{
                    \"id\":\"N-1\",\"name\":\"x\",\"typeLabel\":\"전투\",\"rule\":\"r\",\"phase\":\"COMBAT\",\"danger\":\"LOW\",\"requiresKey\":false,\"keyRequiredReason\":null
                  }],
                  \"defaultShopOffers\": [{\"offerId\":\"O-1\",\"refId\":\"I-1\",\"price\":1,\"stock\":1,\"bound\":false}]
                }
                """;

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> RunConfigs.load(new ByteArrayResource(invalidJson.getBytes(StandardCharsets.UTF_8))));

        assertEquals("run startingGold must be >= 0", ex.getMessage());
    }
}
