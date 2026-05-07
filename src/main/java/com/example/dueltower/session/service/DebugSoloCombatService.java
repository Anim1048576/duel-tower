package com.example.dueltower.session.service;

import com.example.dueltower.session.dto.CommandRequest;
import com.example.dueltower.session.dto.DebugSoloCombatRequest;
import com.example.dueltower.session.dto.DebugSoloCombatResponse;
import com.example.dueltower.session.runtime.SessionRuntime;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DebugSoloCombatService {

    private static final String DEFAULT_GM_PLAYER_ID = "debug-gm";

    private final SessionLifecycleService sessionLifecycleService;
    private final SessionLobbyService sessionLobbyService;
    private final SessionCommandService sessionCommandService;

    public DebugSoloCombatService(SessionLifecycleService sessionLifecycleService,
                                  SessionLobbyService sessionLobbyService,
                                  SessionCommandService sessionCommandService) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.sessionLobbyService = sessionLobbyService;
        this.sessionCommandService = sessionCommandService;
    }

    public DebugSoloCombatResponse startSoloCombat(DebugSoloCombatRequest req) {
        // Quick solo combat creates exactly one real combat actor. GM-NPC control scenarios
        // stay in the dedicated GM-NPC feature/tests so visible player, command actor, and
        // token owner all resolve to the same player id in this debug path.
        String debugPlayerId = normalizeOrDefault(req == null ? null : req.gmPlayerId(), DEFAULT_GM_PLAYER_ID);
        SessionRuntime rt = sessionLifecycleService.createSession(debugPlayerId);

        sessionLobbyService.join(
                rt.code(),
                debugPlayerId,
                req == null ? null : req.playerCharacterId(),
                List.of(),
                null,
                null,
                null
        );
        String playerToken = sessionLobbyService.issuePlayerToken(rt.code(), debugPlayerId);
        sessionLobbyService.setPlayerReady(rt.code(), debugPlayerId, debugPlayerId, true);

        long expectedVersion = sessionLifecycleService.withLockedSession(rt.code(), lockedRt -> lockedRt.state().version());
        sessionCommandService.handleCommand(
                rt.code(),
                rt.gmToken(),
                null,
                new CommandRequest(
                        "START_COMBAT",
                        null,
                        expectedVersion,
                        debugPlayerId,
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

        return new DebugSoloCombatResponse(
                rt.code(),
                debugPlayerId,
                null,
                rt.gmToken(),
                playerToken,
                "/sessions/" + rt.code() + "/combat"
        );
    }

    private static String normalizeOrDefault(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.trim();
    }
}
