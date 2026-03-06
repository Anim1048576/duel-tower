package com.example.dueltower.content.card.service;

import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.engine.core.effect.EffectContext;
import com.example.dueltower.engine.model.CardDefinition;
import com.example.dueltower.engine.model.CardType;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.Zone;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CardServiceTest {

    @Test
    void listFiltersByType() {
        CardBlueprint skillBlueprint = new StubCardBlueprint("C001", CardType.SKILL, false);
        CardBlueprint exBlueprint = new StubCardBlueprint("EX901", CardType.EX, false);
        CardBlueprint tokenBlueprint = new StubCardBlueprint("T001", CardType.TOKEN, true);

        CardService service = new CardService(List.of(skillBlueprint, exBlueprint, tokenBlueprint));

        assertThat(service.list(CardType.SKILL)).extracting(card -> card.id().value())
                .containsExactly("C001");
        assertThat(service.list(CardType.EX)).extracting(card -> card.id().value())
                .containsExactly("EX901");
        assertThat(service.list(CardType.TOKEN)).extracting(card -> card.id().value())
                .containsExactly("T001");
    }

    private static final class StubCardBlueprint implements CardBlueprint {
        private final String id;
        private final CardType type;
        private final boolean token;

        private StubCardBlueprint(String id, CardType type, boolean token) {
            this.id = id;
            this.type = type;
            this.token = token;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public CardDefinition definition() {
            return new CardDefinition(
                    new Ids.CardDefId(id),
                    id,
                    type,
                    0,
                    Map.of(),
                    Zone.DISCARD,
                    token,
                    id + " description"
            );
        }

        @Override
        public void resolve(EffectContext ec) {
        }
    }
}
