package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record SellInventoryItemCommand(
        UUID commandId,
        long expectedVersion,
        PlayerId playerId,
        String itemId,
        String inventoryEquipId,
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
        boolean hasItemId = itemId != null && !itemId.isBlank();
        boolean hasInventoryEquipId = inventoryEquipId != null && !inventoryEquipId.isBlank();
        if (!hasItemId && !hasInventoryEquipId) {
            errors.add("itemId or inventoryEquipId is required");
            return errors;
        }
        if (state.combat() != null || state.nodeState() == NodeState.COMBAT) {
            errors.add("cannot sell inventory item during combat");
            return errors;
        }

        if (hasInventoryEquipId) {
            RunState.InventoryEntry equip = InventoryCommandSupport.findEquipEntryByInventoryId(state, inventoryEquipId);
            if (equip == null) {
                errors.add("equipment not found");
                return errors;
            }
            boolean equippedNow = state.players().values().stream()
                    .flatMap(p -> p.equippedItems().values().stream())
                    .anyMatch(eq -> inventoryEquipId.trim().equals(eq.inventoryEquipId()));
            if (equippedNow) {
                errors.add("equipped equipment cannot be sold");
            }
            return errors;
        }
        if (count <= 0) {
            errors.add("count must be >= 1");
            return errors;
        }
        RunState.InventoryEntry item = InventoryCommandSupport.findItemEntry(state, itemId);
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
        boolean sellingEquip = inventoryEquipId != null && !inventoryEquipId.isBlank();
        String soldEntryId;
        int soldCount;
        int gainedGold;
        if (sellingEquip) {
            RunState.InventoryEntry equip = InventoryCommandSupport.findEquipEntryByInventoryId(state, inventoryEquipId);
            if (equip == null) {
                return List.of();
            }
            InventoryCommandSupport.consumeInventoryEntry(state, equip, 1);
            soldEntryId = ((EquipRef) equip.ref()).equipId();
            soldCount = 1;
            gainedGold = sellUnitPrice(ctx, soldEntryId);
        } else {
            RunState.InventoryEntry item = InventoryCommandSupport.findItemEntry(state, itemId);
            if (item == null) {
                return List.of();
            }
            InventoryCommandSupport.consumeInventoryEntry(state, item, count);
            soldEntryId = ((ItemRef) item.ref()).itemId();
            soldCount = count;
            gainedGold = sellUnitPrice(ctx, soldEntryId) * count;
        }
        state.runState().inventory().gold(state.runState().inventory().gold() + gainedGold);
        state.runState().appendRecentResult(
                "inventory",
                "인벤토리 판매",
                soldEntryId + " x" + soldCount + " 판매",
                "인벤토리 아이템을 정리하여 " + gainedGold + "G를 획득했다.",
                "inventory"
        );

        return List.of(new GameEvent.LogAppended(playerId.value() + " 인벤토리 판매: " + soldEntryId + " x" + soldCount + " (+" + gainedGold + "G)"));
    }

    private static int sellUnitPrice(EngineContext ctx, String itemId) {
        return ctx.rewardTable().sellUnitPrice(itemId);
    }
}
