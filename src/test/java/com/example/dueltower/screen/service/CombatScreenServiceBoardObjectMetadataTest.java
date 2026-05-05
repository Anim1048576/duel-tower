package com.example.dueltower.screen.service;

import com.example.dueltower.content.card.model.playspec.BoardObjectFilter;
import com.example.dueltower.content.card.model.playspec.BoardObjectKind;
import com.example.dueltower.content.card.model.playspec.BoardObjectRelation;
import com.example.dueltower.content.card.model.playspec.CardPlaySpec;
import com.example.dueltower.content.card.model.playspec.SelectBoardObjectsRequirement;
import com.example.dueltower.content.card.model.playspec.TargetSpec;
import com.example.dueltower.session.dto.CardInstanceDto;
import com.example.dueltower.session.dto.CombatStateDto;
import com.example.dueltower.session.dto.PlayerStateDto;
import com.example.dueltower.session.dto.SessionStateDto;
import com.example.dueltower.engine.model.Target;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CombatScreenServiceBoardObjectMetadataTest {

    private final CombatScreenService service = new CombatScreenService(null, null, null);

    @Test
    void allyCharacterCandidateCountUsesAllJoinedPlayersInCoop() {
        SelectBoardObjectsRequirement requirement = new SelectBoardObjectsRequirement(
                1,
                1,
                List.of(BoardObjectKind.CHARACTER),
                BoardObjectRelation.ALLY,
                BoardObjectFilter.ANY,
                false
        );
        SessionStateDto state = sessionState(
                players(
                        player("p1", List.of(), List.of("hand-source")),
                        player("p2", List.of(), List.of())
                ),
                combatState(List.of(), List.of())
        );

        Map<String, Object> hints = invokeBoardObjectSelectionHints(requirement, state, "hand-source", "p1");

        assertThat(hints).containsEntry("candidateCount", 2);
        assertThat(hints).containsEntry("allowedCounts", List.of(1));
        assertThat(hints).containsEntry("skipCountChoice", true);
    }

    @Test
    void hostileOneToTwoSelectionSkipsCountChoiceWhenOnlyOneCandidateExists() {
        SelectBoardObjectsRequirement requirement = new SelectBoardObjectsRequirement(
                1,
                2,
                List.of(BoardObjectKind.CHARACTER, BoardObjectKind.SUMMON),
                BoardObjectRelation.HOSTILE,
                BoardObjectFilter.ANY,
                false
        );
        SessionStateDto state = sessionState(
                players(player("p1", List.of(), List.of("hand-source"))),
                combatState(
                        List.of(new CombatStateDto.EnemyCombatDto("e1", 10, 10, 1, 2, 0, null, false, false, Map.of())),
                        List.of()
                )
        );

        Map<String, Object> hints = invokeBoardObjectSelectionHints(requirement, state, "hand-source", "p1");

        assertThat(hints).containsEntry("candidateCount", 1);
        assertThat(hints).containsEntry("allowedCounts", List.of(1));
        assertThat(hints).containsEntry("skipCountChoice", true);
    }

    @Test
    void hostileOneToTwoSelectionExposesCountChoiceWhenTwoCandidatesExist() {
        SelectBoardObjectsRequirement requirement = new SelectBoardObjectsRequirement(
                1,
                2,
                List.of(BoardObjectKind.CHARACTER, BoardObjectKind.SUMMON),
                BoardObjectRelation.HOSTILE,
                BoardObjectFilter.ANY,
                false
        );
        SessionStateDto state = sessionState(
                players(
                        player("p1", List.of(), List.of("hand-source")),
                        player("p2", List.of(), List.of())
                ),
                combatState(
                        List.of(new CombatStateDto.EnemyCombatDto("e1", 10, 10, 1, 2, 0, null, false, false, Map.of())),
                        List.of(new CombatStateDto.SummonDto("s1", "p2", 5, 1, 0, true))
                )
        );

        Map<String, Object> hints = invokeBoardObjectSelectionHints(requirement, state, "hand-source", "p1");

        assertThat(hints).containsEntry("candidateCount", 2);
        assertThat(hints).containsEntry("allowedCounts", List.of(1, 2));
        assertThat(hints).containsEntry("skipCountChoice", false);
    }

    @Test
    void fieldCardBoardObjectRequirementOwnsMetadataWhenLegacySelectedIdsRequirementIsAbsent() {
        SelectBoardObjectsRequirement requirement = new SelectBoardObjectsRequirement(
                0,
                3,
                List.of(BoardObjectKind.FIELD_CARD),
                BoardObjectRelation.ANY,
                BoardObjectFilter.INSTALLED_ONLY,
                true
        );
        CardPlaySpec playSpec = new CardPlaySpec(
                TargetSpec.none(),
                List.of(requirement)
        );
        SessionStateDto state = sessionState(
                players(
                        player("p1", List.of("field-1"), List.of("hand-source")),
                        player("p2", List.of("field-2"), List.of())
                ),
                combatState(List.of(), List.of())
        );

        Map<String, Object> view = invokeRequirementView(playSpec, "Tig006_Card", state, "hand-source", "p1");

        assertThat(view).containsEntry("selectedIdsSummary", "Select up to 3 installed field card excluding the source card");
        assertThat(view).containsEntry("selectedIdsRequirement", null);
        assertThat(((Map<?, ?>) view.get("boardObjectRequirement")).get("filter")).isEqualTo("INSTALLED_ONLY");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeBoardObjectSelectionHints(SelectBoardObjectsRequirement requirement,
                                                                SessionStateDto state,
                                                                String sourceInstanceId,
                                                                String sourceOwnerPlayerId) {
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                service,
                "boardObjectSelectionHints",
                requirement,
                state,
                sourceInstanceId,
                sourceOwnerPlayerId
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeRequirementView(CardPlaySpec playSpec,
                                                      String sourceLabel,
                                                      SessionStateDto state,
                                                      String sourceInstanceId,
                                                      String sourceOwnerPlayerId) {
        return (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                service,
                "requirementView",
                playSpec,
                sourceLabel,
                state,
                sourceInstanceId,
                sourceOwnerPlayerId
        );
    }

    private SessionStateDto sessionState(Map<String, PlayerStateDto> players,
                                         CombatStateDto combatState) {
        Map<String, CardInstanceDto> cards = new LinkedHashMap<>();
        for (PlayerStateDto player : players.values()) {
            for (String instanceId : player.hand()) {
                cards.put(instanceId, new CardInstanceDto(instanceId, "C001", player.playerId(), "HAND", Map.of(), null, List.of()));
            }
            for (String instanceId : player.field()) {
                cards.put(instanceId, new CardInstanceDto(instanceId, "C001", player.playerId(), "FIELD", Map.of(), null, List.of()));
            }
        }
        return new SessionStateDto(
                "TEST1234",
                "session-1",
                1L,
                1L,
                "COMBAT",
                players,
                combatState,
                cards,
                null
        );
    }

    private Map<String, PlayerStateDto> players(PlayerStateDto... players) {
        Map<String, PlayerStateDto> playerMap = new LinkedHashMap<>();
        for (PlayerStateDto player : players) {
            playerMap.put(player.playerId(), player);
        }
        return playerMap;
    }

    private PlayerStateDto player(String playerId,
                                  List<String> fieldIds,
                                  List<String> handIds) {
        return new PlayerStateDto(
                playerId,
                true,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                handIds,
                List.of(),
                fieldIds,
                List.of(),
                null,
                false,
                null,
                false,
                0,
                false,
                5,
                5,
                0,
                99,
                false,
                List.of(),
                "HUMAN",
                playerId
        );
    }

    private CombatStateDto combatState(List<CombatStateDto.EnemyCombatDto> enemies,
                                       List<CombatStateDto.SummonDto> summons) {
        return new CombatStateDto(
                1,
                List.of("P:p1"),
                0,
                "P:p1",
                "PLAYER",
                Map.of(),
                List.of(),
                summons,
                enemies
        );
    }
}
