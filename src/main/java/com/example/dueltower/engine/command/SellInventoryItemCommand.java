package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.RunState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record SellInventoryItemCommand(
        UUID commandId,
        long expectedVersion,
        PlayerId playerId,
        String itemId,
        int count
) implements GameCommand {

    private static final Map<String, Integer> SELL_UNIT_PRICES = Map.of(
            "I-1", 25,
            "I-2", 100,
            "I-3", 250,
            "I-4", 25,
            "I-5", 100,
            "I-6", 125,
            "I-7", 250
    );

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

        int gainedGold = sellUnitPrice(item.itemId()) * count;
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

    private static int sellUnitPrice(String itemId) {
        return SELL_UNIT_PRICES.getOrDefault(itemId, 0);
    }
}
