package com.example.dueltower.engine.core.effect.passive;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PlayerState;

import java.util.List;

public record PassiveRuntime(
        GameState state,
        EngineContext ctx,
        List<GameEvent> out,
        String source
) {
    public PlayerState player(Ids.PlayerId id) {
        PlayerState ps = state.player(id);
        if (ps == null) throw new IllegalArgumentException("missing player: " + id.value());
        return ps;
    }
}
