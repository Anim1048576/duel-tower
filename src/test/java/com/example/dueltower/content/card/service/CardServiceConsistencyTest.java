package com.example.dueltower.content.card.service;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Zone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CardServiceConsistencyTest {

    @Test
    void constructorShouldFailWhenDefinitionIdAndBlueprintIdMismatch() {
        CardBlueprint brokenBlueprint = new TestCardBlueprint(
                "Tig001_Card",
                new CardDefinition(
                        new CardDefId("Tig999_Card"),
                        "Broken",
                        CardType.SKILL,
                        1,
                        Map.of(),
                        Zone.GRAVE,
                        false,
                        "broken"
                )
        );

        assertThatThrownBy(() -> new CardService(List.of(brokenBlueprint)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("definition id mismatch")
                .hasMessageContaining("Tig999_Card")
                .hasMessageContaining("Tig001_Card");
    }

    @Test
    void constructorShouldFailWhenDuplicateCardDefinitionIdExists() {
        CardDefinition duplicatedDefinition = new CardDefinition(
                new CardDefId("Tig001_Card"),
                "Duplicate",
                CardType.SKILL,
                1,
                Map.of(),
                Zone.GRAVE,
                false,
                "duplicate"
        );
        CardBlueprint first = new TestCardBlueprint("Tig001_Card", duplicatedDefinition);
        CardBlueprint second = new TestCardBlueprint("Tig001_Card", duplicatedDefinition);

        assertThatThrownBy(() -> new CardService(List.of(first, second)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate card id")
                .hasMessageContaining("Tig001_Card");
    }

    private record TestCardBlueprint(String id, CardDefinition definition) implements CardBlueprint {
        @Override
        public void resolve(EffectContext ec) {
            // 테스트용 no-op
        }
    }
}
