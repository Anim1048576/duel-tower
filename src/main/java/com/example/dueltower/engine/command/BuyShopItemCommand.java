package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.event.GameEvent;
import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids.PlayerId;
import com.example.dueltower.engine.model.NodeState;
import com.example.dueltower.engine.model.RunState;

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
        if (!RunCommandSupport.isEventNodePending(state)) {
            errors.add("shop is not available now");
            return errors;
        }

        RunState.ShopOffer offer = state.runState().findShopOffer(offerId);
        if (offer == null) {
            errors.add("offer not found");
            return errors;
        }

        long totalCost = (long) offer.priceGold() * count;
        if (totalCost > state.runState().inventory().gold()) {
            errors.add("not enough gold");
        }
        return errors;
    }

    @Override
    public List<GameEvent> handle(GameState state, EngineContext ctx) {
        RunState.ShopOffer offer = state.runState().findShopOffer(offerId);
        if (offer == null) {
            return List.of();
        }

        InventoryCommandSupport.addInventoryItemCount(state, offer.item(), count);
        int totalCost = offer.priceGold() * count;

        state.runState().resolveCurrentNode(
                "reward",
                "상점 구매 완료",
                offer.item().name() + " x" + count + " 구매",
                "상점에서 " + offer.item().name() + "을(를) 구매했다. 총 " + totalCost + "G 지불.",
                -totalCost,
                0,
                0
        );

        return List.of(new GameEvent.LogAppended(playerId.value() + " 상점 구매: " + offer.id() + " x" + count));
    }
}
