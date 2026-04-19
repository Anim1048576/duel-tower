package com.example.dueltower.content.card.service;

import com.example.dueltower.content.card.dto.CardDetailResponse;
import com.example.dueltower.content.card.model.CardBlueprint;
import com.example.dueltower.content.card.model.playspec.BoardObjectKind;
import com.example.dueltower.content.card.model.playspec.BoardObjectRelation;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.card.model.playspec.DiscardFromHandRequirement;
import com.example.dueltower.content.card.model.playspec.SelectBoardObjectsRequirement;
import com.example.dueltower.content.card.model.playspec.SelectFieldCardsRequirement;
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
    void tig001ShouldExposeTargetAndInstalledSelectionPlaySpec() {
        CardBlueprint card = cardBlueprints.stream()
                .filter(bp -> bp.id().equals("Tig001_Card"))
                .findFirst()
                .orElseThrow();

        CardPlaySpec playSpec = card.playSpec();

        assertThat(playSpec.target().target()).isEqualTo(Target.ENEMY_ONE);
        assertThat(playSpec.target().requiredSelection()).isTrue();
        assertThat(playSpec.extraRequirements())
                .singleElement()
                .isInstanceOfSatisfying(SelectFieldCardsRequirement.class, req -> {
                    assertThat(req.minSelections()).isEqualTo(0);
                    assertThat(req.maxSelections()).isEqualTo(1);
                    assertThat(req.scope().name()).isEqualTo("ALL_PLAYER_FIELDS");
                    assertThat(req.filter().name()).isEqualTo("INSTALLED_ONLY");
                    assertThat(req.excludeSourceCard()).isTrue();
                });
    }

    @Test
    void tigDiscardCardsShouldExposeDiscardRequirementInPlaySpec() {
        assertDiscardOneFromHand("Tig004_Card", true, Target.ENEMY_ONE);
        assertDiscardOneFromHand("Tig005_Card", false, Target.NONE);
        assertDiscardOneFromHand("Tig008_Card", true, Target.ENEMY_ONE);
    }

    @Test
    void tig006ShouldExposeDiscardAndInstalledSelectionRequirementsInPlaySpec() {
        CardDetailResponse detail = cardService.get("Tig006_Card");
        CardPlaySpec playSpec = detail.playSpec();

        assertThat(playSpec.target().requiredSelection()).isFalse();
        assertThat(playSpec.target().target()).isEqualTo(Target.NONE);
        assertThat(playSpec.extraRequirements()).hasSize(2);
        assertThat(playSpec.extraRequirements().get(0))
                .isInstanceOfSatisfying(DiscardFromHandRequirement.class, discard -> {
                    assertThat(discard.count()).isEqualTo(1);
                    assertThat(discard.excludeSourceCard()).isTrue();
                });
        assertThat(playSpec.extraRequirements().get(1))
                .isInstanceOfSatisfying(SelectFieldCardsRequirement.class, req -> {
                    assertThat(req.minSelections()).isEqualTo(0);
                    assertThat(req.maxSelections()).isEqualTo(3);
                    assertThat(req.scope().name()).isEqualTo("ALL_PLAYER_FIELDS");
                    assertThat(req.filter().name()).isEqualTo("INSTALLED_ONLY");
                    assertThat(req.excludeSourceCard()).isTrue();
                });
    }

    @Test
    void tig901ShouldExposeHostileBoardObjectSelectionRequirement() {
        CardDetailResponse detail = cardService.get("Tig901_EX");
        CardPlaySpec playSpec = detail.playSpec();

        assertThat(playSpec.target().requiredSelection()).isFalse();
        assertThat(playSpec.target().target()).isEqualTo(Target.NONE);
        assertThat(playSpec.extraRequirements())
                .singleElement()
                .isInstanceOfSatisfying(SelectBoardObjectsRequirement.class, req -> {
                    assertThat(req.minSelections()).isEqualTo(1);
                    assertThat(req.maxSelections()).isEqualTo(2);
                    assertThat(req.kinds()).containsExactly(BoardObjectKind.CHARACTER, BoardObjectKind.SUMMON);
                    assertThat(req.relation()).isEqualTo(BoardObjectRelation.HOSTILE);
                    assertThat(req.excludeSourceCard()).isFalse();
                });
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

    @Test
    void tig001CardDetailShouldExposeInstalledSelectionPlaySpec() {
        CardDetailResponse detail = cardService.get("Tig001_Card");

        assertThat(detail.playSpec()).isNotNull();
        assertThat(detail.playSpec().target().target()).isEqualTo(Target.ENEMY_ONE);
        assertThat(detail.playSpec().target().requiredSelection()).isTrue();
        assertThat(detail.playSpec().extraRequirements())
                .singleElement()
                .isInstanceOf(SelectFieldCardsRequirement.class);
    }

    @Test
    void basicAttackCardDetailShouldExposeEnemyTargetPlaySpec() {
        CardDetailResponse detail = cardService.get("C001");

        assertThat(detail.playSpec()).isNotNull();
        assertThat(detail.playSpec().target().target()).isEqualTo(Target.ENEMY_ONE);
        assertThat(detail.playSpec().target().requiredSelection()).isTrue();
        assertThat(detail.playSpec().extraRequirements()).isEmpty();
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
