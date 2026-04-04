package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.RunState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record SellInventoryItemCommand(
        UUID commandId,
        long expectedVersion,
        PlayerId playerId,
        String itemId,
        int count
) implements GameCommand {

    private static final Set<String> BATTLE_USABLE_IDS = Set.of("I-1", "I-2", "I-4");

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
        if (itemId == null || itemId.isBlank()) {
            errors.add("itemId is required");
            return errors;
        }
        if (count <= 0) {
            errors.add("count must be >= 1");
            return errors;
        }
        if (state.combat() != null || state.nodeState() == NodeState.COMBAT) {
            errors.add("cannot sell inventory item during combat");
            return errors;
        }

        RunState.InventoryItem item = InventoryCommandSupport.findInventoryItem(state, itemId);
        if (item == null) {
            errors.add("item not found");
            return errors;
        }
        if (item.count() < count) {
            errors.add("not enough item count");
        }
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        RunState.InventoryItem item = InventoryCommandSupport.findInventoryItem(state, itemId);
        if (item == null) {
            return List.of();
        }
        InventoryCommandSupport.consumeInventoryItem(state, item, count);

        int gainedGold = sellUnitPrice(item) * count;
        state.runState().inventory().gold(state.runState().inventory().gold() + gainedGold);
        state.runState().appendRecentResult(
                "inventory",
                "인벤토리 판매",
                item.itemId() + " x" + count + " 판매",
                "인벤토리 아이템을 정리하여 " + gainedGold + "G를 획득했다.",
                "inventory"
        );

        return List.of(new GameEvent.LogAppended(playerId.value() + " 인벤토리 판매: " + item.itemId() + " x" + count + " (+" + gainedGold + "G)"));
    }

    private static int sellUnitPrice(RunState.InventoryItem item) {
        if (item.bound()) {
            return 40;
        }
        if (BATTLE_USABLE_IDS.contains(item.itemId())) {
            return 60;
        }
        return 50;
    }
}
