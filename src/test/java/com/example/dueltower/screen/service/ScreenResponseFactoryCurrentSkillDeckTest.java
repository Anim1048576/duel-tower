package com.example.dueltower.screen.service;

import com.example.dueltower.character.domain.CharacterGender;
import com.example.dueltower.character.domain.CharacterProfile;
import com.example.dueltower.character.dto.CharacterProfileResponse;
import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.character.service.CharacterLoadoutService;
import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.screen.dto.GmLobbyScreenResponse;
import com.example.dueltower.screen.dto.PresetEditorDraftDto;
import com.example.dueltower.screen.dto.PresetEditorResolvedDto;
import com.example.dueltower.session.dto.OwnedCardDto;
import com.example.dueltower.session.dto.PlayerStateDto;
import com.example.dueltower.session.dto.SessionStateDto;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.session.service.SessionAccessDecision;
import com.example.dueltower.session.service.StartCombatAvailabilityService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScreenResponseFactoryCurrentSkillDeckTest {

    @Test
    void gmLobbyCharacterSummaryScoresCurrentSkillDeckByResolvedCardIdsWhenStoredAsCardIds() {
        ScreenResponseFactory factory = factory(mock(CharacterProfileRepository.class));
        PlayerStateDto player = player(
                "player1",
                List.of(
                        owned("p-oc-1", "C001"),
                        owned("p-oc-2", "C002")
                ),
                List.of("p-oc-1", "p-oc-2")
        );
        SessionStateDto state = state(player);
        SessionRuntime runtime = runtime("player1");

        GmLobbyScreenResponse response = factory.gmLobby(
                ScreenRouteSpec.GM_LOBBY,
                state,
                runtime,
                new SessionAccessDecision(SessionAccessDecision.SessionAccessSource.GM_TOKEN, "ABCD1234", "gm", null),
                List.of(characterResponse(
                        7L,
                        "Card Match",
                        """
                                [
                                  {"ownedCardId":"char-oc-1","cardId":"C001"},
                                  {"ownedCardId":"char-oc-2","cardId":"C002"}
                                ]
                                """,
                        List.of("C001", "C002")
                )),
                List.of(),
                List.of(),
                List.of()
        );

        assertThat(response.getParticipantCards()).hasSize(1);
        assertThat(response.getParticipantCards().get(0).characterSummary())
                .isEqualTo("Likely Card Match #7");
    }

    @Test
    void gmLobbyCharacterSummaryScoresCurrentSkillDeckByResolvedPreviewWhenStoredAsOwnedCardIds() {
        ScreenResponseFactory factory = factory(mock(CharacterProfileRepository.class));
        PlayerStateDto player = player(
                "player1",
                List.of(
                        owned("p-oc-1", "C001"),
                        owned("p-oc-2", "C002")
                ),
                List.of("p-oc-1", "p-oc-2")
        );
        SessionStateDto state = state(player);
        SessionRuntime runtime = runtime("player1");

        GmLobbyScreenResponse response = factory.gmLobby(
                ScreenRouteSpec.GM_LOBBY,
                state,
                runtime,
                new SessionAccessDecision(SessionAccessDecision.SessionAccessSource.GM_TOKEN, "ABCD1234", "gm", null),
                List.of(characterResponse(
                        7L,
                        "Owned Match",
                        """
                                [
                                  {"ownedCardId":"char-oc-1","cardId":"C001"},
                                  {"ownedCardId":"char-oc-2","cardId":"C002"}
                                ]
                                """,
                        List.of("C001", "C002")
                )),
                List.of(),
                List.of(),
                List.of()
        );

        assertThat(response.getParticipantCards()).hasSize(1);
        assertThat(response.getParticipantCards().get(0).characterSummary())
                .isEqualTo("Likely Owned Match #7");
    }

    @Test
    void gmLobbyCharacterSummaryDoesNotScoreUnresolvedEntriesAbsentFromPreview() {
        ScreenResponseFactory factory = factory(mock(CharacterProfileRepository.class));
        PlayerStateDto player = player(
                "player1",
                List.of(
                        owned("p-oc-1", "C001"),
                        owned("p-oc-2", "C002")
                ),
                List.of("p-oc-1", "p-oc-2")
        );
        SessionStateDto state = state(player);
        SessionRuntime runtime = runtime("player1");

        GmLobbyScreenResponse response = factory.gmLobby(
                ScreenRouteSpec.GM_LOBBY,
                state,
                runtime,
                new SessionAccessDecision(SessionAccessDecision.SessionAccessSource.GM_TOKEN, "ABCD1234", "gm", null),
                List.of(characterResponse(
                        7L,
                        "Partially Stale",
                        """
                                [
                                  {"ownedCardId":"char-oc-1","cardId":"C001"}
                                ]
                                """,
                        List.of("C001")
                )),
                List.of(),
                List.of(),
                List.of()
        );

        assertThat(response.getParticipantCards()).hasSize(1);
        assertThat(response.getParticipantCards().get(0).characterSummary())
                .isEqualTo("Likely Partially Stale #7");
        assertThat(response.getParticipantCards().get(0).characterSummary())
                .doesNotContain("oc-stale");
    }

    @Test
    void presetEditorResolvedUsesAppliedCardsTagFromCharacterLoadoutPreview() {
        CharacterProfileRepository repository = mock(CharacterProfileRepository.class);
        when(repository.findById(7L)).thenReturn(Optional.of(characterProfile(
                7L,
                "Preset Character",
                "[]",
                List.of()
        )));
        CharacterLoadoutService loadoutService = mock(CharacterLoadoutService.class);
        when(loadoutService.getCurrentSkillDeckPreviewCardIds(7L)).thenReturn(List.of("C001", "C001", "C002"));
        ScreenResponseFactory factory = factory(repository, loadoutService);

        PresetEditorResolvedDto resolved = factory.presetEditorResolved(new PresetEditorDraftDto(
                "preset",
                7L,
                List.of(),
                null,
                List.of()
        ));

        assertThat(resolved.characterTags())
                .extracting(tag -> tag.label())
                .contains("3 applied cards")
                .doesNotContain("3 linked cards");
    }

    @Test
    void presetEditorResolvedOmitsAppliedCardsTagWhenCharacterLoadoutPreviewIsEmpty() {
        CharacterProfileRepository repository = mock(CharacterProfileRepository.class);
        when(repository.findById(7L)).thenReturn(Optional.of(characterProfile(
                7L,
                "Empty Loadout Character",
                "[]",
                List.of()
        )));
        CharacterLoadoutService loadoutService = mock(CharacterLoadoutService.class);
        when(loadoutService.getCurrentSkillDeckPreviewCardIds(7L)).thenReturn(List.of());
        ScreenResponseFactory factory = factory(repository, loadoutService);

        PresetEditorResolvedDto resolved = factory.presetEditorResolved(new PresetEditorDraftDto(
                "preset",
                7L,
                List.of(),
                null,
                List.of()
        ));

        assertThat(resolved.characterTags())
                .extracting(tag -> tag.label())
                .doesNotContain("1 applied cards")
                .doesNotContain("2 applied cards")
                .doesNotContain("linked cards");
    }

    private static ScreenResponseFactory factory(CharacterProfileRepository repository) {
        CharacterLoadoutService loadoutService = mock(CharacterLoadoutService.class);
        when(loadoutService.getCurrentSkillDeckPreviewCardIds(org.mockito.ArgumentMatchers.anyLong())).thenReturn(List.of());
        return factory(repository, loadoutService);
    }

    private static ScreenResponseFactory factory(CharacterProfileRepository repository, CharacterLoadoutService loadoutService) {
        CardService cardService = mock(CardService.class);
        PassiveService passiveService = mock(PassiveService.class);
        StartCombatAvailabilityService startCombatAvailabilityService = mock(StartCombatAvailabilityService.class);
        when(cardService.asMap()).thenReturn(Map.of());
        when(passiveService.defsMap()).thenReturn(Map.of());
        when(startCombatAvailabilityService.analyze(org.mockito.ArgumentMatchers.any(SessionRuntime.class), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new StartCombatAvailabilityService.StartCombatAvailability("player1", null));
        return new ScreenResponseFactory(
                repository,
                loadoutService,
                cardService,
                passiveService,
                startCombatAvailabilityService
        );
    }

    private static SessionRuntime runtime(String playerId) {
        GameState state = new GameState(new SessionId(UUID.randomUUID()), 11L);
        PlayerState playerState = new PlayerState(new PlayerId(playerId));
        state.players().put(playerState.playerId(), playerState);
        return new SessionRuntime("ABCD1234", "gm", "gm-token", state, new EngineContext(Map.of(), Map.of()));
    }

    private static SessionStateDto state(PlayerStateDto player) {
        return new SessionStateDto(
                "ABCD1234",
                "session-1",
                3L,
                11L,
                "NON_COMBAT",
                Map.of(player.playerId(), player),
                null,
                Map.of(),
                null
        );
    }

    private static PlayerStateDto player(String playerId, List<OwnedCardDto> ownedCards, List<String> deckOwnedCardIds) {
        return new PlayerStateDto(
                playerId,
                false,
                List.of(),
                ownedCards,
                List.of(),
                deckOwnedCardIds,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "",
                false,
                null,
                false,
                0,
                false,
                6,
                5,
                ownedCards.size(),
                20,
                false,
                List.of()
        );
    }

    private static OwnedCardDto owned(String ownedCardId, String cardId) {
        return new OwnedCardDto(
                ownedCardId,
                cardId,
                List.of(),
                false,
                false,
                false,
                true,
                null
        );
    }

    private static CharacterProfileResponse characterResponse(
            Long id,
            String name,
            String ownedCards,
            List<String> currentSkillDeck
    ) {
        return new CharacterProfileResponse(
                id,
                name,
                CharacterGender.MALE,
                20,
                "wish",
                "test/test",
                "oneLiner",
                "story",
                5,
                5,
                5,
                5,
                null,
                null,
                List.of(),
                List.of(),
                currentSkillDeck,
                null,
                null,
                null,
                null
        );
    }

    private static CharacterProfile characterProfile(
            Long id,
            String name,
            String ownedCards,
            List<String> currentSkillDeck
    ) {
        return CharacterProfile.builder()
                .id(id)
                .name(name)
                .gender(CharacterGender.MALE)
                .age(20)
                .wish("wish")
                .disposition("test/test")
                .oneLiner("oneLiner")
                .story("story")
                .physical(5)
                .technique(5)
                .sense(5)
                .willpower(5)
                .trait1(null)
                .trait2(null)
                .build();
    }
}
