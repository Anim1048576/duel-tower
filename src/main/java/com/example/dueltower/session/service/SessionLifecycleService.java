package com.example.dueltower.session.service;

import com.example.dueltower.session.runtime.SessionRuntime;
import org.springframework.stereotype.Service;

import java.util.function.Function;

@Service
/**
 * Session lifecycle facade.
 *
 * <p>create/get/delete/expire cleanup과 runtime 접근 진입점을 제공한다.</p>
 */
public class SessionLifecycleService {

    private final SessionService sessionService;

    public SessionLifecycleService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    public SessionRuntime createSession(String gmId) {
        return sessionService.createSession(gmId);
    }

    public SessionRuntime get(String code) {
        return sessionService.get(code);
    }

    public <T> T withSessionLock(String code, Function<SessionRuntime, T> reader) {
        return sessionService.withSessionLock(code, reader);
    }

    public void deleteSession(String code) {
        sessionService.deleteSession(code);
    }

    public void cleanupExpiredSessions() {
        sessionService.cleanupExpiredSessions();
    }
}
