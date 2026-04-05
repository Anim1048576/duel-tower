package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record OpenChestCommand(
        UUID commandId,
        long expectedVersion,
        PlayerId playerId,
        int count
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
        if (count <= 0) {
            errors.add("count must be >= 1");
        }
        if (state.combat() != null || state.nodeState() == NodeState.COMBAT) {
            errors.add("cannot open chest during combat");
        }
        if (state.runState().currentNode() != null && !state.runState().resultPending()) {
            errors.add("cannot open chest while resolving node");
        }
        if (state.runState().inventory().chests() < count) {
            errors.add("not enough chests");
        }
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        int gainedGold = ctx.rewardTable().chest().goldPerChest() * count;
        String rewardItemId = ctx.rewardTable().chest().itemId();
        int gainedItemCount = ctx.rewardTable().chest().itemCountPerChest() * count;
        String rewardItemLabel = ctx.hasItemDef(rewardItemId)
                ? ctx.itemDef(rewardItemId).name()
                : rewardItemId;

        InventoryCommandSupport.addInventoryEntryCount(
                state,
                new ItemRef(rewardItemId),
                false,
                gainedItemCount
        );

        state.runState().resolveCurrentNode(
                "reward",
                "상자 개봉",
                "상자 " + count + "개 개봉",
                "상자에서 " + gainedGold + "G와 " + rewardItemLabel + " " + gainedItemCount + "개를 획득했다.",
                gainedGold,
                0,
                -count
        );

        return List.of(new GameEvent.LogAppended(playerId.value() + " 상자 개봉: " + count + "개"));
    }
}
