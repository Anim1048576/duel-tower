package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.EquipRef;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.InventoryEntryRef;
import com.example.dueltower.engine.model.ItemRef;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.RunState;
import com.example.dueltower.engine.model.Ids.PlayerId;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record BuyShopItemCommand(
        UUID commandId,
        long expectedVersion,
        PlayerId playerId,
        String offerId,
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
        if (offerId == null || offerId.isBlank()) {
            errors.add("offerId is required");
            return errors;
        }
        if (count <= 0) {
            errors.add("count must be >= 1");
        }
        if (state.combat() != null || state.nodeState() == NodeState.COMBAT) {
            errors.add("cannot buy shop item during combat");
            return errors;
        }
        if (!RunCommandSupport.isEventNodePending(state) || !state.runState().shopState().open()) {
            errors.add("shop is not available now");
            return errors;
        }

        RunState.ShopOfferState offer = state.runState().findShopOffer(offerId);
        if (offer == null) {
            errors.add("offer not found");
            return errors;
        }
        if (offer.stockRemaining() <= 0) {
            errors.add("offer out of stock");
            return errors;
        }
        if (count > offer.stockRemaining()) {
            errors.add("not enough stock");
            return errors;
        }

        long totalCost = (long) offer.price() * count;
        if (totalCost > state.runState().inventory().gold()) {
            errors.add("not enough gold");
        }
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        RunState.ShopOfferState offer = state.runState().findShopOffer(offerId);
        if (offer == null) {
            return List.of();
        }
        if (!state.runState().decrementShopOfferStock(offerId, count)) {
            return List.of();
        }

        InventoryCommandSupport.addInventoryEntryCount(
                state,
                ctx,
                offer.ref(),
                offer.bound(),
                count
        );
        int totalCost = offer.price() * count;
        state.runState().inventory().gold(state.runState().inventory().gold() - totalCost);
        state.runState().appendRecentResult(
                "shop_purchase",
                "상점 구매",
                inventoryRefLabel(offer.ref()) + " x" + count + " 구매",
                "상점에서 " + inventoryRefLabel(offer.ref()) + " x" + count + " 구매. 총 " + totalCost + "G 지불.",
                state.runState().currentNode() == null ? "" : state.runState().currentNode().name()
        );

        return List.of(new GameEvent.LogAppended(playerId.value() + " shop purchase: " + offer.offerId() + " x" + count));
    }

    private static String inventoryRefLabel(InventoryEntryRef ref) {
        if (ref instanceof ItemRef itemRef) {
            return itemRef.itemId();
        }
        if (ref instanceof EquipRef equipRef) {
            return equipRef.equipId();
        }
        return "unknown";
    }
}
