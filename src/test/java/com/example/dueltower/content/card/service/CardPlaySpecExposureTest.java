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
        assertThat(playSpec.extraRequirements()).hasSize(2);
        assertThat(playSpec.extraRequirements().get(0))
                .isInstanceOfSatisfying(SelectBoardObjectsRequirement.class, req -> {
                    assertThat(req.minSelections()).isEqualTo(0);
                    assertThat(req.maxSelections()).isEqualTo(1);
                    assertThat(req.kinds()).containsExactly(BoardObjectKind.FIELD_CARD);
                    assertThat(req.relation()).isEqualTo(BoardObjectRelation.ANY);
                    assertThat(req.filter().name()).isEqualTo("INSTALLED_ONLY");
                    assertThat(req.excludeSourceCard()).isTrue();
                });
        assertThat(playSpec.extraRequirements().get(1))
                .isInstanceOfSatisfying(SelectFieldCardsRequirement.class, req -> {
                    assertThat(req.minSelections()).isEqualTo(0);
                    assertThat(req.maxSelections()).isEqualTo(1);
                    assertThat(req.scope().name()).isEqualTo("ALL_PLAYER_FIELDS");
                    assertThat(req.filter().name()).isEqualTo("INSTALLED_ONLY");
                    assertThat(req.excludeSourceCard()).isTrue();
                });
    }

    @Test
    void tig002ShouldExposeAllyBoardObjectSelectionRequirement() {
        CardDetailResponse detail = cardService.get("Tig002_Card");
        CardPlaySpec playSpec = detail.playSpec();

        assertThat(playSpec.target().requiredSelection()).isTrue();
        assertThat(playSpec.target().target()).isEqualTo(Target.ALLY_ONE);
        assertThat(playSpec.extraRequirements())
                .singleElement()
                .isInstanceOfSatisfying(SelectBoardObjectsRequirement.class, req -> {
                    assertThat(req.minSelections()).isEqualTo(1);
                    assertThat(req.maxSelections()).isEqualTo(1);
                    assertThat(req.kinds()).containsExactly(BoardObjectKind.CHARACTER, BoardObjectKind.SUMMON);
                    assertThat(req.relation()).isEqualTo(BoardObjectRelation.ALLY);
                    assertThat(req.excludeSourceCard()).isFalse();
                });
    }

    @Test
    void tigDiscardCardsShouldExposeDiscardRequirementInPlaySpec() {
        assertHostileSingleTarget("Tig003_Card");
        assertDiscardOneFromHand("Tig004_Card", true, Target.ENEMY_ONE, true);
        assertDiscardOneFromHand("Tig005_Card", false, Target.NONE);
        assertDiscardOneFromHand("Tig008_Card", true, Target.ENEMY_ONE, true);
    }

    @Test
    void tig006ShouldExposeDiscardAndInstalledSelectionRequirementsInPlaySpec() {
        CardDetailResponse detail = cardService.get("Tig006_Card");
        CardPlaySpec playSpec = detail.playSpec();

        assertThat(playSpec.target().requiredSelection()).isFalse();
        assertThat(playSpec.target().target()).isEqualTo(Target.NONE);
        assertThat(playSpec.extraRequirements()).hasSize(3);
        assertThat(playSpec.extraRequirements().get(0))
                .isInstanceOfSatisfying(DiscardFromHandRequirement.class, discard -> {
                    assertThat(discard.count()).isEqualTo(1);
                    assertThat(discard.excludeSourceCard()).isTrue();
                });
        assertThat(playSpec.extraRequirements().get(1))
                .isInstanceOfSatisfying(SelectBoardObjectsRequirement.class, req -> {
                    assertThat(req.minSelections()).isEqualTo(0);
                    assertThat(req.maxSelections()).isEqualTo(3);
                    assertThat(req.kinds()).containsExactly(BoardObjectKind.FIELD_CARD);
                    assertThat(req.relation()).isEqualTo(BoardObjectRelation.ANY);
                    assertThat(req.filter().name()).isEqualTo("INSTALLED_ONLY");
                    assertThat(req.excludeSourceCard()).isTrue();
                });
        assertThat(playSpec.extraRequirements().get(2))
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
        assertThat(detail.playSpec().extraRequirements()).hasSize(2);
        assertHostileBoardObjectSelection(detail.playSpec());
        assertThat(detail.playSpec().extraRequirements().get(1)).isInstanceOf(DiscardFromHandRequirement.class);
    }

    @Test
    void tig001CardDetailShouldExposeInstalledSelectionPlaySpec() {
        CardDetailResponse detail = cardService.get("Tig001_Card");

        assertThat(detail.playSpec()).isNotNull();
        assertThat(detail.playSpec().target().target()).isEqualTo(Target.ENEMY_ONE);
        assertThat(detail.playSpec().target().requiredSelection()).isTrue();
        assertThat(detail.playSpec().extraRequirements()).hasSize(2);
        assertThat(detail.playSpec().extraRequirements().get(0)).isInstanceOf(SelectBoardObjectsRequirement.class);
        assertThat(detail.playSpec().extraRequirements().get(1)).isInstanceOf(SelectFieldCardsRequirement.class);
    }

    @Test
    void basicAttackCardDetailShouldExposeEnemyTargetPlaySpec() {
        CardDetailResponse detail = cardService.get("C001");

        assertThat(detail.playSpec()).isNotNull();
        assertThat(detail.playSpec().target().target()).isEqualTo(Target.ENEMY_ONE);
        assertThat(detail.playSpec().target().requiredSelection()).isTrue();
        assertHostileBoardObjectSelection(detail.playSpec());
    }

    @Test
    void basicRecoveryAndExWrapShouldExposeAllyBoardObjectSelectionRequirement() {
        assertAllySingleTarget("C002", Target.ALLY_ONE);
        assertAllySingleTarget("EX901", Target.ALLY_ONE);
    }

    @Test
    void basicCurseAndTigHostileCardsShouldExposeHostileBoardObjectSelectionRequirement() {
        assertHostileSingleTarget("C004");
        assertHostileSingleTarget("Tig004_Card");
        assertHostileSingleTarget("Tig008_Card");
    }

    private void assertDiscardOneFromHand(String cardId, boolean targetRequired, Target expectedTarget) {
        assertDiscardOneFromHand(cardId, targetRequired, expectedTarget, false);
    }

    private void assertDiscardOneFromHand(String cardId,
                                          boolean targetRequired,
                                          Target expectedTarget,
                                          boolean expectHostileBoardRequirement) {
        CardDetailResponse detail = cardService.get(cardId);
        CardPlaySpec playSpec = detail.playSpec();

        assertThat(playSpec.target().requiredSelection()).isEqualTo(targetRequired);
        assertThat(playSpec.target().target()).isEqualTo(expectedTarget);
        int discardIndex = expectHostileBoardRequirement ? 1 : 0;
        if (expectHostileBoardRequirement) {
            assertHostileBoardObjectSelection(playSpec);
        }
        assertThat(playSpec.extraRequirements().get(discardIndex))
                .isInstanceOfSatisfying(DiscardFromHandRequirement.class, discard -> {
                    assertThat(discard.count()).isEqualTo(1);
                    assertThat(discard.excludeSourceCard()).isTrue();
                });
    }

    private void assertHostileSingleTarget(String cardId) {
        CardDetailResponse detail = cardService.get(cardId);
        CardPlaySpec playSpec = detail.playSpec();

        assertThat(playSpec.target().requiredSelection()).isTrue();
        assertThat(playSpec.target().target()).isEqualTo(Target.ENEMY_ONE);
        assertHostileBoardObjectSelection(playSpec);
    }

    private void assertAllySingleTarget(String cardId, Target expectedTarget) {
        CardDetailResponse detail = cardService.get(cardId);
        CardPlaySpec playSpec = detail.playSpec();

        assertThat(playSpec.target().requiredSelection()).isTrue();
        assertThat(playSpec.target().target()).isEqualTo(expectedTarget);
        assertThat(playSpec.extraRequirements())
                .singleElement()
                .isInstanceOfSatisfying(SelectBoardObjectsRequirement.class, req -> {
                    assertThat(req.minSelections()).isEqualTo(1);
                    assertThat(req.maxSelections()).isEqualTo(1);
                    assertThat(req.kinds()).containsExactly(BoardObjectKind.CHARACTER, BoardObjectKind.SUMMON);
                    assertThat(req.relation()).isEqualTo(BoardObjectRelation.ALLY);
                    assertThat(req.excludeSourceCard()).isFalse();
                });
    }

    private void assertHostileBoardObjectSelection(CardPlaySpec playSpec) {
        assertThat(playSpec.extraRequirements().get(0))
                .isInstanceOfSatisfying(SelectBoardObjectsRequirement.class, req -> {
                    assertThat(req.minSelections()).isEqualTo(1);
                    assertThat(req.maxSelections()).isEqualTo(1);
                    assertThat(req.kinds()).containsExactly(BoardObjectKind.CHARACTER, BoardObjectKind.SUMMON);
                    assertThat(req.relation()).isEqualTo(BoardObjectRelation.HOSTILE);
                    assertThat(req.excludeSourceCard()).isFalse();
                });
    }
}
