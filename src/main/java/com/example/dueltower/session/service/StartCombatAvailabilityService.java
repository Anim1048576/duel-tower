package com.example.dueltower.session.service;

import com.example.dueltower.common.api.ApiErrorException;
import com.example.dueltower.common.api.ApiErrorResponse;
import com.example.dueltower.common.api.ApiErrorResolver;
import com.example.dueltower.engine.model.CardInstance;
import com.example.dueltower.engine.model.CombatPhase;
import com.example.dueltower.engine.model.CombatState;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.session.runtime.SessionRuntime;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class StartCombatAvailabilityService {

    private final SessionLoadoutSupport sessionLoadoutSupport;

    public StartCombatAvailabilityService(SessionLoadoutSupport sessionLoadoutSupport) {
        this.sessionLoadoutSupport = sessionLoadoutSupport;
    }

    public StartCombatAvailability analyze(SessionRuntime rt, String requestedStartPlayerId) {
        return analyze(rt.state(), requestedStartPlayerId);
    }

    public StartCombatAvailability analyze(GameState state, String requestedStartPlayerId) {
        List<PlayerState> players = sortedPlayers(state);
        String recommendedStartPlayerId = players.stream()
                .filter(PlayerState::ready)
                .map(player -> player.playerId().value())
                .findFirst()
                .orElse(null);

        CombatState combat = state.combat();
        if (combat != null && combat.phase() != CombatPhase.END) {
            return blocked(
                    recommendedStartPlayerId,
                    "COMBAT_ALREADY_ACTIVE",
                    "Combat is already active for this session.",
                    "session.combat.phase != END",
                    Map.of("phase", combat.phase().name()),
                    "combat already started"
            );
        }

        if (players.isEmpty()) {
            return blocked(
                    recommendedStartPlayerId,
                    "PARTICIPANT_REQUIRED",
                    "At least one participant must join before combat can start.",
                    "state.players is empty",
                    null,
                    "no players joined"
            );
        }

        List<String> unreadyPlayerIds = players.stream()
                .filter(player -> !player.ready())
                .map(player -> player.playerId().value())
                .toList();
        if (!unreadyPlayerIds.isEmpty()) {
            return blocked(
                    recommendedStartPlayerId,
                    "READY_PARTICIPANT_REQUIRED",
                    "cannot start combat until all required players are ready",
                    "unready participants are present",
                    ApiErrorResolver.details("playerIds", unreadyPlayerIds),
                    "cannot start combat until all required players are ready"
            );
        }

        String normalizedStartPlayerId = normalize(requestedStartPlayerId);
        if (normalizedStartPlayerId != null) {
            PlayerState requestedPlayer = state.player(new PlayerId(normalizedStartPlayerId));
            if (requestedPlayer == null) {
                return blocked(
                        recommendedStartPlayerId,
                        "START_PLAYER_NOT_FOUND",
                        "cannot start combat with an unknown start player",
                        "requested start player does not exist",
                        ApiErrorResolver.details("playerId", normalizedStartPlayerId),
                        "start player not found"
                );
            }
        }

        List<String> invalidLoadoutPlayerIds = invalidLoadoutPlayerIds(state, players);
        if (!invalidLoadoutPlayerIds.isEmpty()) {
            return blocked(
                    recommendedStartPlayerId,
                    "DECK_INVALID",
                    "cannot start combat because one or more decks are invalid",
                    "start combat loadout validation failed",
                    ApiErrorResolver.details("playerIds", invalidLoadoutPlayerIds),
                    "cannot start combat because one or more decks are invalid"
            );
        }

        return new StartCombatAvailability(recommendedStartPlayerId, null);
    }

    private List<String> invalidLoadoutPlayerIds(GameState state, List<PlayerState> players) {
        List<String> invalid = new ArrayList<>();
        for (PlayerState player : players) {
            if (!isValidStartLoadout(state, player)) {
                invalid.add(player.playerId().value());
            }
        }
        return List.copyOf(invalid);
    }

    private boolean isValidStartLoadout(GameState state, PlayerState player) {
        try {
            sessionLoadoutSupport.validateDeckBuild(player.deckOwnedCardIds(), player.ownedCards(), null);
            CardInstance exCard = player.exCard() == null ? null : state.card(player.exCard());
            if (exCard == null || exCard.defId() == null || exCard.defId().value() == null || exCard.defId().value().isBlank()) {
                return false;
            }
            sessionLoadoutSupport.validateExCardId(exCard.defId().value());
            return true;
        } catch (ApiErrorException | ResponseStatusException | IllegalArgumentException ex) {
            return false;
        }
    }

    private List<PlayerState> sortedPlayers(GameState state) {
        return state.players().values().stream()
                .sorted(Comparator.comparing((PlayerState player) -> player.ready()).reversed()
                        .thenComparing(player -> player.playerId().value()))
                .toList();
    }

    private StartCombatAvailability blocked(String recommendedStartPlayerId,
                                            String code,
                                            String userMessage,
                                            String debugMessage,
                                            Object details,
                                            String commandError) {
        return new StartCombatAvailability(
                recommendedStartPlayerId,
                new StartCombatBlockReason(
                        code,
                        "RULE",
                        userMessage,
                        debugMessage,
                        details,
                        409,
                        commandError
                )
        );
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    public record StartCombatAvailability(
            String recommendedStartPlayerId,
            StartCombatBlockReason blockedReason
    ) {
        public boolean allowed() {
            return blockedReason == null;
        }

        public List<String> errors() {
            return allowed() ? List.of() : List.of(blockedReason.commandError());
        }

        public ApiErrorResponse apiError() {
            if (allowed()) {
                return null;
            }
            return ApiErrorResponse.of(
                    blockedReason.code(),
                    blockedReason.category(),
                    blockedReason.userMessage(),
                    blockedReason.debugMessage(),
                    blockedReason.details(),
                    blockedReason.status(),
                    null
            );
        }
    }

    public record StartCombatBlockReason(
            String code,
            String category,
            String userMessage,
            String debugMessage,
            Object details,
            int status,
            String commandError
    ) {}
}
