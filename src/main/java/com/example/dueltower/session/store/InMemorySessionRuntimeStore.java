package com.example.dueltower.session.store;

import com.example.dueltower.session.runtime.SessionRuntime;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Current in-memory implementation of {@link SessionRuntimeStore}.
 *
 * <p>This class deliberately owns only runtime storage. TTL/expire policy and
 * lock entry are coordinated by the lifecycle layer.</p>
 */
@Component
public class InMemorySessionRuntimeStore implements SessionRuntimeStore {

    private final Map<String, SessionRuntime> sessions = new ConcurrentHashMap<>();

    @Override
    public boolean putIfAbsent(String code, SessionRuntime runtime) {
        return sessions.putIfAbsent(code, runtime) == null;
    }

    @Override
    public SessionRuntime get(String code) {
        return sessions.get(code);
    }

    @Override
    public SessionRuntime remove(String code) {
        return sessions.remove(code);
    }

    @Override
    public boolean remove(String code, SessionRuntime runtime) {
        return sessions.remove(code, runtime);
    }

    @Override
    public List<Map.Entry<String, SessionRuntime>> entries() {
        return List.copyOf(sessions.entrySet());
    }
}
