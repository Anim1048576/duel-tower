package com.example.dueltower.session.service;

import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.session.runtime.SessionRuntime;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static com.example.dueltower.session.service.SessionAccessDecision.SessionAccessSource.AUTHENTICATED_GM;
import static com.example.dueltower.session.service.SessionAccessDecision.SessionAccessSource.AUTHENTICATED_PLAYER;
import static com.example.dueltower.session.service.SessionAccessDecision.SessionAccessSource.GM_TOKEN;
import static com.example.dueltower.session.service.SessionAccessDecision.SessionAccessSource.PLAYER_TOKEN;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class SessionAccessResolver {

    public SessionAccessDecision requireReadable(SessionRuntime rt,
                                                 String gmTokenHeader,
                                                 String playerTokenHeader,
                                                 Authentication authentication) {
        String gmToken = normalizeToken(gmTokenHeader);
        if (!gmToken.isEmpty() && rt.gmToken().equals(gmToken)) {
            return new SessionAccessDecision(GM_TOKEN, null, null);
        }

        String playerId = resolvePlayerIdByToken(rt, playerTokenHeader);
        if (playerId != null && hasParticipant(rt, playerId)) {
            return new SessionAccessDecision(PLAYER_TOKEN, null, playerId);
        }

        String username = authenticatedUsername(authentication);
        if (username != null) {
            if (rt.gmId().equals(username)) {
                return new SessionAccessDecision(AUTHENTICATED_GM, username, null);
            }
            if (hasParticipant(rt, username)) {
                return new SessionAccessDecision(AUTHENTICATED_PLAYER, username, username);
            }
            throw new ResponseStatusException(FORBIDDEN, "session read forbidden");
        }

        throw new ResponseStatusException(UNAUTHORIZED, "session read authorization required");
    }

    public void requireGm(SessionRuntime rt, String gmTokenHeader) {
        String gmToken = normalizeToken(gmTokenHeader);
        if (!gmToken.isEmpty() && rt.gmToken().equals(gmToken)) {
            return;
        }

        throw new ResponseStatusException(UNAUTHORIZED, "gm authorization required");
    }

    public String requirePlayerToken(SessionRuntime rt, String playerTokenHeader) {
        String playerId = resolvePlayerIdByToken(rt, playerTokenHeader);
        if (playerId != null && hasParticipant(rt, playerId)) {
            return playerId;
        }

        throw new ResponseStatusException(UNAUTHORIZED, "player authorization required");
    }

    public String requirePlayerSelf(SessionRuntime rt,
                                    String playerTokenHeader,
                                    String targetPlayerId,
                                    String mismatchMessage) {
        String actorPlayerId = requirePlayerToken(rt, playerTokenHeader);
        String normalizedTargetPlayerId = normalizeRequiredPlayerId(targetPlayerId);
        if (!normalizedTargetPlayerId.equals(actorPlayerId)) {
            throw new ResponseStatusException(FORBIDDEN, mismatchMessage);
        }
        return actorPlayerId;
    }

    public String authenticatedUsername(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return null;
        }
        return authentication.getName();
    }

    public String normalizeToken(String token) {
        return token == null ? "" : token.trim();
    }

    public String resolvePlayerIdByToken(SessionRuntime rt, String playerTokenHeader) {
        String token = normalizeToken(playerTokenHeader);
        if (token.isEmpty()) {
            return null;
        }
        return rt.withLock(() -> rt.findPlayerIdByToken(token));
    }

    private String normalizeRequiredPlayerId(String playerId) {
        if (playerId == null || playerId.isBlank()) {
            throw new ResponseStatusException(UNAUTHORIZED, "player authorization required");
        }
        return playerId.trim();
    }

    private boolean hasParticipant(SessionRuntime rt, String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return false;
        }
        PlayerId id = new PlayerId(playerId.trim());
        return rt.withLock(() -> rt.state().players().containsKey(id));
    }
}
