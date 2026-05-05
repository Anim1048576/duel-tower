package com.example.dueltower.session.service;

import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.PlayerControlType;
import com.example.dueltower.engine.model.PlayerState;
import com.example.dueltower.session.dto.ControllableActorDto;
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
import java.util.List;

@Service
/**
 * Session authorization policy summary:
 * - PUBLIC_SESSION_STATE: handled outside this resolver and always public.
 * - AUTHENTICATED_SESSION_ENTRY: handled by Spring Security and requires login.
 * - SESSION_READABLE: any valid GM/player token wins; login fallback is allowed only when both token headers are absent.
 *   If a non-blank token header is present but no valid GM/player token succeeds, login fallback is blocked and 401 is returned.
 * - PLAYER_SELF: requires a valid player token that matches the target playerId.
 * - GM_ONLY: requires a valid GM token.
 *
 * Lock note:
 * This resolver still uses {@link SessionRuntime#withLock(java.util.function.Supplier)}
 * directly because callers already resolved a concrete runtime and need authorization
 * decisions against that exact instance before continuing the same flow.
 */
public class SessionAccessResolver {

    public SessionAccessDecision requireSessionReadable(SessionRuntime rt,
                                                        String gmTokenHeader,
                                                        String playerTokenHeader,
                                                        Authentication authentication) {
        String gmToken = normalizeToken(gmTokenHeader);
        boolean hasGmTokenHeader = hasTokenHeader(gmTokenHeader);
        if (hasGmTokenHeader && rt.gmToken().equals(gmToken)) {
            return new SessionAccessDecision(GM_TOKEN, rt.code(), rt.gmId(), null);
        }

        String normalizedPlayerToken = normalizeToken(playerTokenHeader);
        boolean hasPlayerTokenHeader = hasTokenHeader(playerTokenHeader);
        String playerId = resolvePlayerIdByToken(rt, normalizedPlayerToken);
        if (playerId != null && hasParticipant(rt, playerId)) {
            return new SessionAccessDecision(PLAYER_TOKEN, rt.code(), null, playerId);
        }

        // Mixed-token rule: any valid GM/player token already returned above.
        // Reaching this branch means token headers may exist, but none authorized the read.
        if (hasGmTokenHeader || hasPlayerTokenHeader) {
            throw new ResponseStatusException(UNAUTHORIZED, "invalid session read token");
        }

        String username = authenticatedUsername(authentication);
        if (username != null) {
            if (rt.gmId().equals(username)) {
                return new SessionAccessDecision(AUTHENTICATED_GM, rt.code(), username, null);
            }
            if (hasParticipant(rt, username)) {
                return new SessionAccessDecision(AUTHENTICATED_PLAYER, rt.code(), username, username);
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

    public String requirePlayerControl(SessionRuntime rt,
                                       String playerTokenHeader,
                                       String actorPlayerId,
                                       String mismatchMessage) {
        String requesterPlayerId = requirePlayerToken(rt, playerTokenHeader);
        String normalizedActorPlayerId = normalizeRequiredPlayerId(actorPlayerId);
        if (!canControl(rt, requesterPlayerId, normalizedActorPlayerId)) {
            throw new ResponseStatusException(FORBIDDEN, mismatchMessage);
        }
        return requesterPlayerId;
    }

    public boolean canControl(SessionRuntime rt, String requesterPlayerIdRaw, String actorPlayerIdRaw) {
        if (rt == null || requesterPlayerIdRaw == null || requesterPlayerIdRaw.isBlank()
                || actorPlayerIdRaw == null || actorPlayerIdRaw.isBlank()) {
            return false;
        }
        String requesterPlayerId = requesterPlayerIdRaw.trim();
        String actorPlayerId = actorPlayerIdRaw.trim();
        if (requesterPlayerId.equals(actorPlayerId)) {
            return hasParticipant(rt, actorPlayerId);
        }

        return rt.withLock(() -> {
            PlayerState actor = rt.state().players().get(new PlayerId(actorPlayerId));
            if (actor == null || actor.controlType() != PlayerControlType.GM_CONTROLLED_NPC) {
                return false;
            }
            String controllerPlayerId = actor.controllerPlayerId().value();
            return requesterPlayerId.equals(controllerPlayerId) || requesterPlayerId.equals(rt.gmId());
        });
    }

    public List<ControllableActorDto> controllableActors(SessionRuntime rt, String requesterPlayerIdRaw) {
        if (rt == null || requesterPlayerIdRaw == null || requesterPlayerIdRaw.isBlank()) {
            return List.of();
        }
        String requesterPlayerId = requesterPlayerIdRaw.trim();
        return rt.withLock(() -> rt.state().players().values().stream()
                .filter(player -> canControl(rt, requesterPlayerId, player.playerId().value()))
                .map(player -> new ControllableActorDto(
                        player.playerId().value(),
                        player.playerId().value(),
                        player.controlType().name()
                ))
                .toList());
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

    private boolean hasTokenHeader(String tokenHeader) {
        return !normalizeToken(tokenHeader).isEmpty();
    }

    private boolean hasParticipant(SessionRuntime rt, String playerId) {
        if (playerId == null || playerId.isBlank()) {
            return false;
        }
        PlayerId id = new PlayerId(playerId.trim());
        return rt.withLock(() -> rt.state().players().containsKey(id));
    }
}
