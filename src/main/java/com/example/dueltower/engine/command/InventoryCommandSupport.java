package com.example.dueltower.engine.command;

import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.RunState;

import java.util.ArrayList;
import java.util.List;

final class InventoryCommandSupport {
    private InventoryCommandSupport() {}

    static RunState.InventoryItem findInventoryItem(GameState state, String rawItemId) {
        if (rawItemId == null || rawItemId.isBlank()) {
            return null;
        }
        String normalized = rawItemId.trim();
        for (RunState.InventoryItem item : state.runState().inventory().items()) {
            if (normalized.equals(item.id())) {
                return item;
            }
        }
        return null;
    }

    static void consumeInventoryItem(GameState state, RunState.InventoryItem usedItem, int usedCount) {
        List<RunState.InventoryItem> nextItems = new ArrayList<>();
        for (RunState.InventoryItem item : state.runState().inventory().items()) {
            if (!item.id().equals(usedItem.id())) {
                nextItems.add(item);
                continue;
            }

            int remain = Math.max(0, item.count() - usedCount);
            if (remain > 0) {
                nextItems.add(new RunState.InventoryItem(
                        item.id(),
                        item.name(),
                        remain,
                        item.bound(),
                        item.battleUsable(),
                        item.summary(),
                        item.description(),
                        item.tags()
                ));
            }
        }
        state.runState().inventory().replaceItems(nextItems);
    }

    static void addInventoryItemCount(GameState state, RunState.InventoryItem gainedItem, int gainedCount) {
        if (gainedItem == null || gainedCount <= 0) {
            return;
        }
        boolean merged = false;
        List<RunState.InventoryItem> nextItems = new ArrayList<>();
        for (RunState.InventoryItem item : state.runState().inventory().items()) {
            if (!item.id().equals(gainedItem.id())) {
                nextItems.add(item);
                continue;
            }
            nextItems.add(new RunState.InventoryItem(
                    item.id(),
                    item.name(),
                    item.count() + gainedCount,
                    item.bound(),
                    item.battleUsable(),
                    item.summary(),
                    item.description(),
                    item.tags()
            ));
            merged = true;
        }
        if (!merged) {
            nextItems.add(new RunState.InventoryItem(
                    gainedItem.id(),
                    gainedItem.name(),
                    gainedCount,
                    gainedItem.bound(),
                    gainedItem.battleUsable(),
                    gainedItem.summary(),
                    gainedItem.description(),
                    gainedItem.tags()
            ));
        }
        state.runState().inventory().replaceItems(nextItems);
    }
}
