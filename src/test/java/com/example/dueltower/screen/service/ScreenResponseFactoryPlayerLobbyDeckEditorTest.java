package com.example.dueltower.screen.service;

import com.example.dueltower.character.repository.CharacterProfileRepository;
import com.example.dueltower.character.service.CharacterLoadoutService;
import com.example.dueltower.content.card.service.CardService;
import com.example.dueltower.content.passive.service.PassiveService;
import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.Ids.SessionId;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.screen.dto.PlayerLobbyScreenResponse;
import com.example.dueltower.session.dto.OwnedCardDto;
import com.example.dueltower.session.dto.PlayerStateDto;
import com.example.dueltower.session.dto.SessionStateDto;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.session.service.PlayerLobbyDeckEditAnalysis;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ScreenResponseFactoryPlayerLobbyDeckEditorTest {

    @Test
    void playerLobbyProjectsDeckEditorStateFromServerAnalysis() {
        CharacterProfileRepository characterProfileRepository = mock(CharacterProfileRepository.class);
        CharacterLoadoutService characterLoadoutService = mock(CharacterLoadoutService.class);
        CardService cardService = mock(CardService.class);
        PassiveService passiveService = mock(PassiveService.class);
        when(cardService.asMap()).thenReturn(Map.of());
        ScreenResponseFactory factory = new ScreenResponseFactory(characterProfileRepository, characterLoadoutService, cardService, passiveService);

        SessionRuntime runtime = runtime("player1");
        PlayerStateDto me = player(
                "player1",
                List.of(
                        owned("oc-1", "C001", true),
                        owned("oc-2", "C001", false),
                        owned("oc-3", "C002", false)
                ),
                List.of("oc-1", "oc-3")
        );
        SessionStateDto state = new SessionStateDto(
                "ABCD1234",
                "session-1",
                3L,
                11L,
                "NON_COMBAT",
                Map.of("player1", me),
                null,
                Map.of(),
                null
        );
        PlayerLobbyDeckEditAnalysis analysis = new PlayerLobbyDeckEditAnalysis(
                new PlayerLobbyDeckEditAnalysis.DeckState(12, 11, 3, false),
                List.of(
                        new PlayerLobbyDeckEditAnalysis.Issue(
                                PlayerLobbyDeckEditAnalysis.IssueLevel.ERROR,
                                PlayerLobbyDeckEditAnalysis.IssueCode.INVALID_DECK_SIZE,
                                Map.of("requiredDeckSize", 12, "actualDeckSize", 11)
                        ),
                        new PlayerLobbyDeckEditAnalysis.Issue(
                                PlayerLobbyDeckEditAnalysis.IssueLevel.ERROR,
                                PlayerLobbyDeckEditAnalysis.IssueCode.REPLACEMENT_LIMIT_REACHED,
                                Map.of("maxChangedCards", 2, "actualChangedCards", 3)
                        )
                ),
                List.of(
                        new PlayerLobbyDeckEditAnalysis.DeckEntryAnalysis(
                                "oc-1",
                                "C001",
                                true,
                                true,
                                false,
                                List.of(PlayerLobbyDeckEditAnalysis.IssueCode.LOCKED_CARD)
                        ),
                        new PlayerLobbyDeckEditAnalysis.DeckEntryAnalysis(
                                "oc-3",
                                "C002",
                                true,
                                false,
                                true,
                                List.of()
                        )
                ),
                List.of(
                        new PlayerLobbyDeckEditAnalysis.CardPoolGroupAnalysis(
                                "C001",
                                1,
                                2,
                                1,
                                false,
                                List.of(PlayerLobbyDeckEditAnalysis.IssueCode.REPLACEMENT_LIMIT_REACHED)
                        ),
                        new PlayerLobbyDeckEditAnalysis.CardPoolGroupAnalysis(
                                "C002",
                                1,
                                1,
                                0,
                                false,
                                List.of(PlayerLobbyDeckEditAnalysis.IssueCode.ALREADY_IN_DECK)
                        )
                )
        );

        PlayerLobbyScreenResponse response = factory.playerLobby(
                ScreenRouteSpec.PLAYER_LOBBY,
                state,
                runtime,
                "player1",
                me,
                analysis,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("notice")
        );

        assertThat(response.getDeckEditor()).isNotNull();
        assertThat(response.getDeckEditor().deck().changedCardCount()).isEqualTo(3);
        assertThat(response.getDeckEditor().deck().draftDeckSize()).isEqualTo(11);
        assertThat(response.getDeckEditor().deck().saveAllowed()).isFalse();
        assertThat(response.getDeckEditor().globalReasonCodes()).containsExactly(
                "INVALID_DECK_SIZE",
                "REPLACEMENT_LIMIT_REACHED"
        );
        assertThat(response.getDeckEditor().issues())
                .extracting(issue -> issue.code())
                .containsExactly("INVALID_DECK_SIZE", "REPLACEMENT_LIMIT_REACHED");
        assertThat(response.getDeckEditor().draftEntries())
                .filteredOn(entry -> entry.ownedCardId().equals("oc-1"))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.canRemove()).isFalse();
                    assertThat(entry.reasonCodes()).containsExactly("LOCKED_CARD");
                });
        assertThat(response.getDeckEditor().cardPoolGroups())
                .filteredOn(group -> group.cardId().equals("C001"))
                .singleElement()
                .satisfies(group -> {
                    assertThat(group.canAdd()).isFalse();
                    assertThat(group.reasonCodes()).containsExactly("REPLACEMENT_LIMIT_REACHED");
                    assertThat(group.ownedCards())
                            .filteredOn(card -> card.ownedCardId().equals("oc-1"))
                            .singleElement()
                            .satisfies(card -> {
                                assertThat(card.inDraftDeck()).isTrue();
                                assertThat(card.canAdd()).isFalse();
                                assertThat(card.reasonCodes()).containsExactly("ALREADY_IN_DECK");
                            });
                    assertThat(group.ownedCards())
                            .filteredOn(card -> card.ownedCardId().equals("oc-2"))
                            .singleElement()
                            .satisfies(card -> {
                                assertThat(card.inDraftDeck()).isFalse();
                                assertThat(card.canAdd()).isFalse();
                                assertThat(card.reasonCodes()).containsExactly("REPLACEMENT_LIMIT_REACHED");
                            });
                });
    }

    private static SessionRuntime runtime(String playerId) {
        GameState state = new GameState(new SessionId(UUID.randomUUID()), 11L);
        PlayerState playerState = new PlayerState(new PlayerId(playerId));
        state.players().put(playerState.playerId(), playerState);
        return new SessionRuntime("ABCD1234", "gm", "gm-token", state, new EngineContext(Map.of(), Map.of()));
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

    private static OwnedCardDto owned(String ownedCardId, String cardId, boolean lockedInDeck) {
        return new OwnedCardDto(
                ownedCardId,
                cardId,
                List.of(),
                false,
                false,
                lockedInDeck,
                true,
                null
        );
    }
}
