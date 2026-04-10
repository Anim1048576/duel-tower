package com.example.dueltower.session.api;

import com.example.dueltower.session.dto.CommandRequest;
import com.example.dueltower.session.runtime.SessionRuntime;
import com.example.dueltower.session.service.SessionAccessResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@Component
public class SessionCommandAuthorization {

    private final SessionAccessResolver sessionAccessResolver;

    public SessionCommandAuthorization(SessionAccessResolver sessionAccessResolver) {
        this.sessionAccessResolver = sessionAccessResolver;
    }

    public void authorize(SessionRuntime rt,
                          SessionCommandType commandType,
                          CommandRequest req,
                          String gmTokenHeader,
                          String playerTokenHeader) {
        if (commandType.requiresPlayerId()) {
            requireText(req.trimmedPlayerId(), "playerId");
        }

        if (commandType.requiresGmAuthorization()) {
            sessionAccessResolver.requireGm(rt, gmTokenHeader);
            return;
        }

        if (commandType.requiresPlayerAuthorization()) {
            String requestPlayerId = requireText(req.trimmedPlayerId(), "playerId");
            sessionAccessResolver.requirePlayerSelf(rt, playerTokenHeader, requestPlayerId, "playerId mismatch");
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, fieldName + " is required");
        }
        return value.trim();
    }
}
