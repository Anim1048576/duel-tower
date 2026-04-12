package com.example.dueltower.session.store;

import com.example.dueltower.session.runtime.SessionRuntime;

import java.util.List;
import java.util.Map;

/**
 * Mutable runtime store boundary for active sessions.
 *
 * <p>TTL/expire policy stays in the lifecycle layer. Implementations provide
 * only storage, lookup, deletion, and enumeration for live runtimes.
 * Lock coordination stays outside the store.</p>
 */
public interface SessionRuntimeStore {

    boolean putIfAbsent(String code, SessionRuntime runtime);

    SessionRuntime get(String code);

    SessionRuntime remove(String code);

    boolean remove(String code, SessionRuntime runtime);

    List<Map.Entry<String, SessionRuntime>> entries();
}
