package com.example.dueltower.screen.service;

import com.example.dueltower.common.api.ApiErrorResolver;
import com.example.dueltower.common.api.ApiErrorResponse;
import com.example.dueltower.screen.dto.DisabledReasonDto;
import com.example.dueltower.screen.dto.GmLobbyScreenResponse;
import com.example.dueltower.screen.dto.GmLobbyStartCombatActionRequest;
import com.example.dueltower.screen.dto.GmLobbyStartCombatActionResponse;
import com.example.dueltower.session.dto.CommandRequest;
import com.example.dueltower.session.dto.EngineResponseDto;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.session.service.SessionAccessDecision;
import com.example.dueltower.session.service.SessionAccessResolver;
import com.example.dueltower.session.service.SessionLifecycleService;
import com.example.dueltower.session.service.SessionCommandService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
public class GmLobbyStartCombatActionService {

    private final SessionLifecycleService sessionLifecycleService;
    private final SessionAccessResolver sessionAccessResolver;
    private final SessionCommandService sessionCommandService;
    private final GmLobbyScreenService gmLobbyScreenService;

    public GmLobbyStartCombatActionService(SessionLifecycleService sessionLifecycleService,
                                           SessionAccessResolver sessionAccessResolver,
                                           SessionCommandService sessionCommandService,
                                           GmLobbyScreenService gmLobbyScreenService) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.sessionAccessResolver = sessionAccessResolver;
        this.sessionCommandService = sessionCommandService;
        this.gmLobbyScreenService = gmLobbyScreenService;
    }

    public GmLobbyStartCombatActionResponse startCombat(String code,
                                                        String gmTokenHeader,
                                                        String playerTokenHeader,
                                                        Authentication authentication,
                                                        GmLobbyStartCombatActionRequest request) {
        return sessionLifecycleService.withLockedSession(code, rt -> startCombat(rt, gmTokenHeader, playerTokenHeader, authentication, request));
    }

    private GmLobbyStartCombatActionResponse startCombat(SessionRuntime rt,
                                                         String gmTokenHeader,
                                                         String playerTokenHeader,
                                                         Authentication authentication,
                                                         GmLobbyStartCombatActionRequest request) {
        SessionAccessDecision readDecision = resolveReadableDecision(rt, gmTokenHeader, playerTokenHeader, authentication);
        GmActionAccess gmActionAccess = resolveGmActionAccess(rt, gmTokenHeader, authentication);
        if (readDecision == null && gmActionAccess.allowed()) {
            readDecision = gmActionAccess.asReadableDecision(rt);
        }
        if (!gmActionAccess.allowed()) {
            DisabledReasonDto disabledReason = gmActionFailureReason(readDecision, gmActionAccess.disabledReason());
            return failure(
                    "GM_ACCESS_REQUIRED",
                    gmActionAccess.message(),
                    disabledReason,
                    false,
                    gmActionAccess.restoredGmToken(),
                    latestScreen(rt, readDecision)
            );
        }

        GmLobbyScreenResponse currentScreen = latestScreen(rt, readDecision);
        if (currentScreen == null) {
            return failure(
                    "GM_ACCESS_REQUIRED",
                    "Unable to restore the current GM lobby screen before START_COMBAT.",
                    gmActionAccess.disabledReason(),
                    gmActionAccess.restored(),
                    gmActionAccess.restoredGmToken(),
                    null
            );
        }

        if (currentScreen.getStartCombat().blockedReason() != null) {
            DisabledReasonDto blockedReason = currentScreen.getStartCombat().blockedReason();
            if ("COMBAT_ALREADY_ACTIVE".equals(blockedReason.code())) {
                return success(
                        "ALREADY_ACTIVE",
                        "Combat is already active for this session.",
                        gmActionAccess.restored(),
                        gmActionAccess.restoredGmToken(),
                        false,
                        combatRoute(rt.code()),
                        null
                );
            }
            return failure(
                    "BLOCKED",
                    blockedReason.userMessage(),
                    blockedReason,
                    gmActionAccess.restored(),
                    gmActionAccess.restoredGmToken(),
                    currentScreen
            );
        }

        String requestedPlayerId = normalizePlayerId(request == null ? null : request.playerId());
        String startPlayerId = requestedPlayerId == null
                ? normalizePlayerId(currentScreen.getStartCombat().recommendedStartPlayerId())
                : requestedPlayerId;
        if (startPlayerId == null) {
            DisabledReasonDto reason = new DisabledReasonDto(
                    "START_PLAYER_REQUIRED",
                    "VALIDATION",
                    "Select a start player before combat starts from the GM lobby.",
                    "playerId was blank and no recommendedStartPlayerId was available",
                    null,
                    400,
                    null
            );
            return failure(
                    "BLOCKED",
                    reason.userMessage(),
                    reason,
                    gmActionAccess.restored(),
                    gmActionAccess.restoredGmToken(),
                    currentScreen
            );
        }

        Long requestedVersion = request == null ? null : request.expectedVersion();
        long expectedVersion = requestedVersion == null ? currentScreen.getVersion() : requestedVersion;
        EngineResponseDto firstResponse = executeStartCombat(rt.code(), gmActionAccess.gmToken(), startPlayerId, expectedVersion);
        if (firstResponse.accepted()) {
            return success(
                    "STARTED",
                    gmActionAccess.restored()
                            ? "GM access was restored and combat started."
                            : "Combat started.",
                    gmActionAccess.restored(),
                    gmActionAccess.restoredGmToken(),
                    false,
                    combatRoute(rt.code()),
                    null
            );
        }

        if (containsError(firstResponse, "combat already started")) {
            return success(
                    "ALREADY_ACTIVE",
                    "Combat had already started in this session.",
                    gmActionAccess.restored(),
                    gmActionAccess.restoredGmToken(),
                    false,
                    combatRoute(rt.code()),
                    null
            );
        }

        if (containsError(firstResponse, "version mismatch")) {
            EngineResponseDto retryResponse = executeStartCombat(rt.code(), gmActionAccess.gmToken(), startPlayerId, firstResponse.state().version());
            if (retryResponse.accepted()) {
                return success(
                        "STARTED",
                        gmActionAccess.restored()
                                ? "GM access was restored, the latest lobby version was applied, and combat started."
                                : "The latest lobby version was applied and combat started.",
                        gmActionAccess.restored(),
                        gmActionAccess.restoredGmToken(),
                        true,
                        combatRoute(rt.code()),
                        null
                );
            }
            if (containsError(retryResponse, "combat already started")) {
                return success(
                        "ALREADY_ACTIVE",
                        "Combat became active while START_COMBAT was being retried.",
                        gmActionAccess.restored(),
                        gmActionAccess.restoredGmToken(),
                        true,
                        combatRoute(rt.code()),
                        null
                );
            }
            if (containsError(retryResponse, "version mismatch")) {
                DisabledReasonDto reason = new DisabledReasonDto(
                        "VERSION_MISMATCH_RETRY_EXHAUSTED",
                        "CONFLICT",
                        "The GM lobby changed twice while combat was starting. Refresh the latest screen and try again.",
                        String.join("; ", retryResponse.errors()),
                        Map.of("retryUsed", true, "latestVersion", retryResponse.state().version()),
                        409,
                        null
                );
                return failure(
                        "VERSION_MISMATCH_RETRY_EXHAUSTED",
                        reason.userMessage(),
                        reason,
                        gmActionAccess.restored(),
                        gmActionAccess.restoredGmToken(),
                        latestScreen(rt, readDecision),
                        true
                );
            }

            return failure(
                    "FAILED",
                    primaryMessage(retryResponse),
                    toDisabledReason(retryResponse),
                    gmActionAccess.restored(),
                    gmActionAccess.restoredGmToken(),
                    latestScreen(rt, readDecision),
                    true
            );
        }

        return failure(
                "FAILED",
                primaryMessage(firstResponse),
                toDisabledReason(firstResponse),
                gmActionAccess.restored(),
                gmActionAccess.restoredGmToken(),
                latestScreen(rt, readDecision)
        );
    }

    private EngineResponseDto executeStartCombat(String code,
                                                 String gmToken,
                                                 String playerId,
                                                 long expectedVersion) {
        return sessionCommandService.handleCommand(
                code,
                gmToken,
                null,
                new CommandRequest(
                        "START_COMBAT",
                        null,
                        expectedVersion,
                        playerId,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
    }

    private SessionAccessDecision resolveReadableDecision(SessionRuntime rt,
                                                          String gmTokenHeader,
                                                          String playerTokenHeader,
                                                          Authentication authentication) {
        try {
            return sessionAccessResolver.requireSessionReadable(rt, gmTokenHeader, playerTokenHeader, authentication);
        } catch (ResponseStatusException ex) {
            return null;
        }
    }

    private GmActionAccess resolveGmActionAccess(SessionRuntime rt,
                                                 String gmTokenHeader,
                                                 Authentication authentication) {
        String normalizedGmToken = sessionAccessResolver.normalizeToken(gmTokenHeader);
        if (!normalizedGmToken.isBlank() && normalizedGmToken.equals(rt.gmToken())) {
            return GmActionAccess.allowed(false, null, rt.gmToken(), SessionAccessDecision.SessionAccessSource.GM_TOKEN);
        }

        String username = sessionAccessResolver.authenticatedUsername(authentication);
        if (username == null) {
            return GmActionAccess.denied(
                    "GM access is required to start combat from the GM lobby.",
                    new DisabledReasonDto(
                            "GM_ACCESS_REQUIRED",
                            "AUTH",
                            "Sign in as the session GM or provide a valid GM token before starting combat.",
                            "no valid gm token and no authenticated GM session",
                            null,
                            401,
                            null
                    )
            );
        }
        if (!rt.gmId().equals(username)) {
            return GmActionAccess.denied(
                    "The current login cannot restore GM access for this session.",
                    new DisabledReasonDto(
                            "GM_ACCESS_RESTORE_FAILED",
                            "AUTH",
                            "The current login can read this GM lobby, but it cannot restore GM write access for START_COMBAT.",
                            "authenticated username does not match session gmId",
                            Map.of("username", username, "gmId", rt.gmId()),
                            403,
                            null
                    )
            );
        }

        return GmActionAccess.allowed(true, rt.gmToken(), rt.gmToken(), SessionAccessDecision.SessionAccessSource.AUTHENTICATED_GM);
    }

    private GmLobbyScreenResponse latestScreen(SessionRuntime rt,
                                               SessionAccessDecision decision) {
        if (decision == null) {
            return null;
        }
        return gmLobbyScreenService.buildScreen(rt, decision);
    }

    private DisabledReasonDto toDisabledReason(EngineResponseDto response) {
        ApiErrorResponse apiError = (response.errorDetails() == null || response.errorDetails().isEmpty())
                ? ApiErrorResolver.commandRejection(response.errors())
                : response.errorDetails().get(0);
        return DisabledReasonDto.fromApiErrorResponse(apiError);
    }

    private String primaryMessage(EngineResponseDto response) {
        if (response.errorDetails() != null && !response.errorDetails().isEmpty()) {
            return response.errorDetails().get(0).userMessage();
        }
        if (response.errors() != null && !response.errors().isEmpty()) {
            return response.errors().get(0);
        }
        return "START_COMBAT could not be completed.";
    }

    private boolean containsError(EngineResponseDto response,
                                  String fragment) {
        List<String> errors = response.errors();
        return errors != null && errors.stream().anyMatch(error -> error != null && error.contains(fragment));
    }

    private DisabledReasonDto gmActionFailureReason(SessionAccessDecision readDecision,
                                                    DisabledReasonDto fallback) {
        if (readDecision == null || fallback == null) {
            return fallback;
        }
        if (readDecision.source() == SessionAccessDecision.SessionAccessSource.PLAYER_TOKEN
                || readDecision.source() == SessionAccessDecision.SessionAccessSource.AUTHENTICATED_PLAYER) {
            return new DisabledReasonDto(
                    "GM_ACCESS_RESTORE_FAILED",
                    "AUTH",
                    "The current session access can read this GM lobby, but it cannot restore GM write access for START_COMBAT.",
                    "session readable access is not the session GM",
                    Map.of("source", readDecision.source().name()),
                    403,
                    null
            );
        }
        return fallback;
    }

    private String normalizePlayerId(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return null;
        }
        return playerId.trim();
    }

    private String combatRoute(String code) {
        return "/sessions/" + code + "/combat";
    }

    private GmLobbyStartCombatActionResponse success(String outcome,
                                                     String message,
                                                     boolean gmAccessRestored,
                                                     String restoredGmToken,
                                                     boolean retryUsed,
                                                     String nextRoute,
                                                     GmLobbyScreenResponse latestScreen) {
        return new GmLobbyStartCombatActionResponse(
                true,
                outcome,
                message,
                null,
                nextRoute,
                nextRoute == null ? null : "navigate",
                gmAccessRestored,
                restoredGmToken,
                retryUsed,
                latestScreen
        );
    }

    private GmLobbyStartCombatActionResponse failure(String outcome,
                                                     String message,
                                                     DisabledReasonDto disabledReason,
                                                     boolean gmAccessRestored,
                                                     String restoredGmToken,
                                                     GmLobbyScreenResponse latestScreen) {
        return failure(outcome, message, disabledReason, gmAccessRestored, restoredGmToken, latestScreen, false);
    }

    private GmLobbyStartCombatActionResponse failure(String outcome,
                                                     String message,
                                                     DisabledReasonDto disabledReason,
                                                     boolean gmAccessRestored,
                                                     String restoredGmToken,
                                                     GmLobbyScreenResponse latestScreen,
                                                     boolean retryUsed) {
        return new GmLobbyStartCombatActionResponse(
                false,
                outcome,
                message,
                disabledReason,
                null,
                null,
                gmAccessRestored,
                restoredGmToken,
                retryUsed,
                latestScreen
        );
    }

    private record GmActionAccess(
            boolean allowed,
            boolean restored,
            String restoredGmToken,
            String gmToken,
            SessionAccessDecision.SessionAccessSource source,
            String message,
            DisabledReasonDto disabledReason
    ) {
        private static GmActionAccess allowed(boolean restored,
                                             String restoredGmToken,
                                             String gmToken,
                                             SessionAccessDecision.SessionAccessSource source) {
            return new GmActionAccess(true, restored, restoredGmToken, gmToken, source, null, null);
        }

        private static GmActionAccess denied(String message, DisabledReasonDto disabledReason) {
            return new GmActionAccess(false, false, null, null, null, message, disabledReason);
        }

        private SessionAccessDecision asReadableDecision(SessionRuntime rt) {
            if (!allowed || source == null) {
                return null;
            }
            return switch (source) {
                case GM_TOKEN -> new SessionAccessDecision(source, rt.code(), rt.gmId(), null);
                case AUTHENTICATED_GM -> new SessionAccessDecision(source, rt.code(), rt.gmId(), null);
                default -> null;
            };
        }
    }
}
