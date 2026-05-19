package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.NodeState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record LeaveShopCommand(
        UUID commandId,
        long expectedVersion,
        PlayerId playerId
) implements GameCommand {

    @Override
    public List<String> validate(GameState state, EngineContext ctx) {
        List<String> errors = new ArrayList<>();
        if (playerId == null) {
            errors.add("playerId is required");
            return errors;
        }
        if (!state.players().containsKey(playerId)) {
            errors.add("player not found");
            return errors;
        }
        if (state.combat() != null || state.nodeState() == NodeState.COMBAT) {
            errors.add("cannot leave shop during combat");
            return errors;
        }
        if (!RunCommandSupport.isEventNodePending(state) || !state.runState().shopState().open()) {
            errors.add("shop is not available now");
        }
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        state.runState().closeShop();
        state.runState().resolveCurrentNode(
                "shop",
                "상점 이용 완료",
                "상점 이용을 마쳤습니다.",
                "원정대가 상점 정비를 마치고 다음 경로로 이동할 준비를 마쳤습니다.",
                0,
                0,
                0
        );

        return List.of(new GameEvent.LogAppended(playerId.value() + " shop completed"));
    }
}
