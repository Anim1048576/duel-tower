package com.example.dueltower.engine.core;

import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;

import java.util.List;

public record EngineResult(
        boolean accepted,
        List<String> errors,
        List<GameEvent> events,
        GameState state
) {
    public static EngineResult rejected(List<String> errors, GameState state) {
        return new EngineResult(false, List.copyOf(errors), List.of(), state);
    }
    public static EngineResult accepted(List<GameEvent> events, GameState state) {
        return new EngineResult(true, List.of(), List.copyOf(events), state);
    }
}