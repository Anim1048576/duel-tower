package com.example.dueltower.content.card.service;

import com.example.dueltower.content.card.dto.CardDetailResponse;
import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.card.model.playspec.DiscardFromHandRequirement;
import com.example.dueltower.engine.model.Target;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class CardPlaySpecExposureTest {

    @Autowired
    private CardService cardService;

    @Autowired
    private List<CardBlueprint> cardBlueprints;

    @Test
    void defaultPlaySpecShouldBeNoneForCardWithoutOverride() {
        CardBlueprint card = cardBlueprints.stream()
                .filter(bp -> bp.id().equals("Tig001_Card"))
                .findFirst()
                .orElseThrow();

        CardPlaySpec playSpec = card.playSpec();

        assertThat(playSpec.target().target()).isEqualTo(Target.NONE);
        assertThat(playSpec.target().requiredSelection()).isFalse();
        assertThat(playSpec.extraRequirements()).isEmpty();
    }

    @Test
    void tigDiscardCardsShouldExposeDiscardRequirementInPlaySpec() {
        assertDiscardOneFromHand("Tig004_Card", true, Target.ENEMY_ONE);
        assertDiscardOneFromHand("Tig005_Card", false, Target.NONE);
        assertDiscardOneFromHand("Tig006_Card", false, Target.NONE);
        assertDiscardOneFromHand("Tig008_Card", true, Target.ENEMY_ONE);
    }

    @Test
    void cardDetailShouldExposePlaySpec() {
        CardDetailResponse detail = cardService.get("Tig004_Card");

        assertThat(detail.playSpec()).isNotNull();
        assertThat(detail.playSpec().target().target()).isEqualTo(Target.ENEMY_ONE);
        assertThat(detail.playSpec().extraRequirements())
                .singleElement()
                .isInstanceOf(DiscardFromHandRequirement.class);
    }

    private void assertDiscardOneFromHand(String cardId, boolean targetRequired, Target expectedTarget) {
        CardDetailResponse detail = cardService.get(cardId);
        CardPlaySpec playSpec = detail.playSpec();

        assertThat(playSpec.target().requiredSelection()).isEqualTo(targetRequired);
        assertThat(playSpec.target().target()).isEqualTo(expectedTarget);
        assertThat(playSpec.extraRequirements())
                .singleElement()
                .isInstanceOfSatisfying(DiscardFromHandRequirement.class, discard -> {
                    assertThat(discard.count()).isEqualTo(1);
                    assertThat(discard.excludeSourceCard()).isTrue();
                });
    }
}
