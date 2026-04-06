package com.example.dueltower.content.card.service;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.card.dto.CardDetailResponse;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids.CardDefId;
import com.example.dueltower.engine.model.Zone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void listGetAndAsMapShouldAlwaysUseDefinitionFromBlueprint() {
        CardDefinition blueprintDefinition = new CardDefinition(
                new CardDefId("Tig777_Card"),
                "Blueprint Source",
                CardType.SKILL,
                2,
                Map.of("K_TEST", 1),
                Zone.GRAVE,
                false,
                "definition comes from blueprint"
        );
        CardPlaySpec playSpec = CardPlaySpec.none();
        CardBlueprint blueprint = new TestCardBlueprint("Tig777_Card", blueprintDefinition, playSpec);

        CardService service = new CardService(List.of(blueprint));

        CardDefinition listed = service.list().get(0);
        CardDetailResponse detail = service.get(" Tig777_Card ");
        CardDefinition detailed = new CardDefinition(
                detail.id(),
                detail.name(),
                detail.type(),
                detail.cost(),
                detail.keywords(),
                detail.resolveTo(),
                detail.token(),
                detail.description()
        );
        CardDefinition fromMap = service.asMap().get(new CardDefId("Tig777_Card"));

        assertThat(listed).isSameAs(blueprintDefinition);
        assertThat(detailed).isEqualTo(blueprintDefinition);
        assertThat(fromMap).isSameAs(blueprintDefinition);
        assertThat(detail.playSpec()).isSameAs(playSpec);
    }

    private record TestCardBlueprint(String id, CardDefinition definition, CardPlaySpec playSpec) implements CardBlueprint {
        private TestCardBlueprint(String id, CardDefinition definition) {
            this(id, definition, CardPlaySpec.none());
        }

        @Override
        public void resolve(EffectContext ec) {
            // 테스트용 no-op
        }
    }
}
