package com.example.dueltower.session.service;

import com.example.dueltower.common.api.ApiErrorResolver;
import com.example.dueltower.engine.command.GameCommand;
import com.example.dueltower.engine.core.EngineResult;
import com.example.dueltower.session.api.SessionCommandAuthorization;
import com.example.dueltower.session.api.SessionCommandType;
import com.example.dueltower.session.dto.CommandRequest;
import com.example.dueltower.session.dto.EngineResponseDto;
import com.example.dueltower.session.dto.SessionStateDto;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.session.runtime.StateMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Service
@Slf4j
/**
 * Session command write service.
 *
 * <p>command 진입점, expectedVersion 검사, 권한 위임, 엔진 호출, 응답 조립을 담당한다.</p>
 */
public class SessionCommandService {

    private final SessionLifecycleService sessionLifecycleService;
    private final SessionCommandAuthorization sessionCommandAuthorization;

    public SessionCommandService(SessionLifecycleService sessionLifecycleService,
                                 SessionCommandAuthorization sessionCommandAuthorization) {
        this.sessionLifecycleService = sessionLifecycleService;
        this.sessionCommandAuthorization = sessionCommandAuthorization;
    }

    public EngineResponseDto handleCommand(String code,
                                           String gmTokenHeader,
                                           String playerTokenHeader,
                                           CommandRequest req) {
        long startNs = System.nanoTime();

        if (req == null || req.normalizedType().isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "type is required");
        }
        if (req.expectedVersion() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "expectedVersion is required");
        }

        return sessionLifecycleService.withLockedSession(code, rt -> {
            UUID commandId = parseOrNewUuid(req.commandId());
            SessionCommandType commandType = SessionCommandType.from(req.type());

            sessionCommandAuthorization.authorize(rt, commandType, req, gmTokenHeader, playerTokenHeader);

            log.debug("command received code={} type={} playerId={} expectedVersion={} commandId={} cardId={} summonId={} itemId={} count={} reason={} discardIds={} targetPlayers={} targetEnemies={} targets={}",
                    code,
                    commandType.requestType(),
                    req.trimmedPlayerId(),
                    req.expectedVersion(),
                    commandId,
                    req.trimmedCardId(),
                    req.trimmedSummonId(),
                    req.trimmedItemId(),
                    req.count(),
                    req.trimmedReason(),
                    (req.discardIds() == null) ? 0 : req.discardIds().size(),
                    (req.targetPlayerIds() == null) ? 0 : req.targetPlayerIds().size(),
                    (req.targetEnemyIds() == null) ? 0 : req.targetEnemyIds().size(),
                    (req.targets() == null) ? 0 : req.targets().size()
            );

            GameCommand cmd = commandType.toCommand(req, commandId, req.expectedVersion());
            final EngineResult res = rt.apply(cmd);

            long tookMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs);
            if (res.accepted()) {
                log.debug("command accepted code={} type={} commandId={} events={} newVersion={} ({}ms)",
                        code, req.type(), commandId, res.events().size(), res.state().version(), tookMs);
            } else {
                log.warn("command rejected code={} type={} commandId={} errors={} version={} ({}ms)",
                        code, req.type(), commandId, res.errors(), res.state().version(), tookMs);
            }

            SessionStateDto state = StateMapper.toDto(rt.code(), res.state());

            return new EngineResponseDto(
                    res.accepted(),
                    res.errors(),
                    res.accepted() ? List.of() : List.of(ApiErrorResolver.commandRejection(res.errors())),
                    StateMapper.toEventDtos(res.events()),
                    state
            );
        });
    }

    private static UUID parseOrNewUuid(String v) {
        if (v == null || v.isBlank()) return UUID.randomUUID();
        try {
            return UUID.fromString(v.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(BAD_REQUEST, "invalid commandId uuid");
        }
    }
}
