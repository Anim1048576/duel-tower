package com.example.dueltower.session.service;

import com.example.dueltower.session.dto.AddGmNpcRequest;
import com.example.dueltower.session.dto.AddGmNpcResponse;
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
    private final SessionGmNpcService sessionGmNpcService;
    private final SessionCommandService sessionCommandService;

    public DebugSoloCombatService(SessionLifecycleService sessionLifecycleService,
                                  SessionLobbyService sessionLobbyService,
                                  SessionGmNpcService sessionGmNpcService,
                                  SessionCommandService sessionCommandService) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.sessionLobbyService = sessionLobbyService;
        this.sessionGmNpcService = sessionGmNpcService;
        this.sessionCommandService = sessionCommandService;
    }

    public DebugSoloCombatResponse startSoloCombat(DebugSoloCombatRequest req) {
        String gmPlayerId = normalizeOrDefault(req == null ? null : req.gmPlayerId(), DEFAULT_GM_PLAYER_ID);
        SessionRuntime rt = sessionLifecycleService.createSession(gmPlayerId);

        sessionLobbyService.join(
                rt.code(),
                gmPlayerId,
                req == null ? null : req.playerCharacterId(),
                List.of(),
                null,
                null,
                null
        );
        String playerToken = sessionLobbyService.issuePlayerToken(rt.code(), gmPlayerId);
        sessionLobbyService.setPlayerReady(rt.code(), gmPlayerId, gmPlayerId, true);

        AddGmNpcResponse npc = sessionGmNpcService.addGmControlledNpc(
                rt.code(),
                gmPlayerId,
                new AddGmNpcRequest(
                        req == null ? null : req.npcName(),
                        req == null ? null : req.npcCharacterId(),
                        null,
                        null,
                        null
                )
        );

        long expectedVersion = sessionLifecycleService.withLockedSession(rt.code(), lockedRt -> lockedRt.state().version());
        sessionCommandService.handleCommand(
                rt.code(),
                rt.gmToken(),
                null,
                new CommandRequest(
                        "START_COMBAT",
                        null,
                        expectedVersion,
                        gmPlayerId,
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
                gmPlayerId,
                npc.npcPlayerId(),
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
