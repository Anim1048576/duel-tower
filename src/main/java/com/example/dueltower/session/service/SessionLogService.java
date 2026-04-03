package com.example.dueltower.session.service;

import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.session.dto.SessionEventItemDto;
import com.example.dueltower.session.dto.SessionEventPageResponse;
import com.example.dueltower.session.dto.SessionLogItemDto;
import com.example.dueltower.session.dto.SessionLogPageResponse;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.session.runtime.StateMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class SessionLogService {

    private static final int DEFAULT_EVENT_LIMIT = 50;
    private static final int MAX_EVENT_LIMIT = 200;
    private static final int DEFAULT_LOG_LIMIT = 50;
    private static final int MAX_LOG_LIMIT = 200;

    private final SessionService sessionService;

    public SessionLogService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public SessionEventPageResponse getEvents(String code,
                                              Long afterVersion,
                                              Integer limit,
                                              String gmTokenHeader,
                                              String playerTokenHeader,
                                              Authentication authentication) {
        long minVersion = normalizeAfterVersion(afterVersion);
        int requestedLimit = normalizeLimit(limit, DEFAULT_EVENT_LIMIT, MAX_EVENT_LIMIT);

        return withAuthorizedRead(code, gmTokenHeader, playerTokenHeader, authentication, rt -> {
            List<SessionRuntime.StoredEvent> history = rt.eventHistorySnapshot();
            List<SessionEventItemDto> items = new ArrayList<>(requestedLimit);

            int matched = 0;
            for (SessionRuntime.StoredEvent stored : history) {
                if (stored.version() <= minVersion) {
                    continue;
                }
                matched++;
                if (items.size() < requestedLimit) {
                    var dto = StateMapper.toEventDto(stored.event());
                    items.add(new SessionEventItemDto(
                            stored.cursor(),
                            stored.version(),
                            dto.type(),
                            dto.payload(),
                            stored.occurredAt()
                    ));
                }
            }

            boolean hasMore = matched > requestedLimit;
            long fromVersion = items.isEmpty() ? minVersion : items.get(0).version();
            long toVersion = items.isEmpty() ? minVersion : items.get(items.size() - 1).version();
            return new SessionEventPageResponse(code, fromVersion, toVersion, List.copyOf(items), hasMore);
        });
    }

    public SessionLogPageResponse getLogs(String code,
                                          Long before,
                                          Integer limit,
                                          String gmTokenHeader,
                                          String playerTokenHeader,
                                          Authentication authentication) {
        long beforeCursor = normalizeBefore(before);
        int requestedLimit = normalizeLimit(limit, DEFAULT_LOG_LIMIT, MAX_LOG_LIMIT);

        return withAuthorizedRead(code, gmTokenHeader, playerTokenHeader, authentication, rt -> {
            List<SessionRuntime.StoredEvent> history = rt.eventHistorySnapshot();
            List<SessionLogItemDto> items = new ArrayList<>(requestedLimit);

            for (int i = history.size() - 1; i >= 0 && items.size() < requestedLimit; i--) {
                SessionRuntime.StoredEvent stored = history.get(i);
                if (stored.cursor() >= beforeCursor) {
                    continue;
                }
                if (!(stored.event() instanceof GameEvent.LogAppended logAppended)) {
                    continue;
                }
                items.add(new SessionLogItemDto(
                        stored.cursor(),
                        stored.version(),
                        "LOG_APPENDED",
                        logAppended.line(),
                        stored.occurredAt()
                ));
            }

            Long nextBefore = (items.size() == requestedLimit)
                    ? items.get(items.size() - 1).cursor()
                    : null;
            return new SessionLogPageResponse(code, List.copyOf(items), nextBefore);
        });
    }

    /**
     * 공통 조회 입구: 세션 접근 + 권한 검증 + read 함수 실행.
     * 이후 recent-results / results / run / inventory alias 조회도 이 경로를 재사용한다.
     */
    private <T> T withAuthorizedRead(String code,
                                     String gmTokenHeader,
                                     String playerTokenHeader,
                                     Authentication authentication,
                                     Function<SessionRuntime, T> reader) {
        SessionRuntime rt = sessionService.get(code);
        ensureReadable(rt, code, gmTokenHeader, playerTokenHeader, authentication);
        return reader.apply(rt);
    }

    private static long normalizeAfterVersion(Long afterVersion) {
        long minVersion = (afterVersion == null) ? 0L : afterVersion;
        if (minVersion < 0L) {
            throw new ResponseStatusException(BAD_REQUEST, "afterVersion must be >= 0");
        }
        return minVersion;
    }

    private static long normalizeBefore(Long before) {
        if (before == null) {
            return Long.MAX_VALUE;
        }
        if (before <= 0L) {
            throw new ResponseStatusException(BAD_REQUEST, "before must be > 0");
        }
        return before;
    }

    private static int normalizeLimit(Integer raw, int defaultLimit, int maxLimit) {
        int v = (raw == null) ? defaultLimit : raw;
        if (v <= 0) {
            throw new ResponseStatusException(BAD_REQUEST, "limit must be > 0");
        }
        return Math.min(v, maxLimit);
    }

    private void ensureReadable(SessionRuntime rt,
                                String code,
                                String gmTokenHeader,
                                String playerTokenHeader,
                                Authentication authentication) {
        String gmToken = normalizeToken(gmTokenHeader);
        if (rt.gmToken().equals(gmToken)) {
            return;
        }

        String resolvedPlayerId = sessionService.resolvePlayerIdByToken(code, playerTokenHeader);
        if (resolvedPlayerId != null && rt.state().players().containsKey(new com.example.dueltower.engine.model.Ids.PlayerId(resolvedPlayerId))) {
            return;
        }

        String username = authenticatedUsername(authentication);
        if (username != null) {
            if (username.equals(rt.gmId())) {
                return;
            }
            if (rt.state().players().containsKey(new com.example.dueltower.engine.model.Ids.PlayerId(username))) {
                return;
            }
            throw new ResponseStatusException(FORBIDDEN, "session read forbidden");
        }

        throw new ResponseStatusException(UNAUTHORIZED, "session read authorization required");
    }

    private static String normalizeToken(String token) {
        return token == null ? "" : token.trim();
    }

    private static String authenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return authentication.getName();
    }
}
