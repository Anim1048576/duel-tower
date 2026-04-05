package com.example.dueltower.engine.command;

import com.example.dueltower.engine.model.*;

import java.util.ArrayList;
import java.util.List;

final class InventoryCommandSupport {
    private InventoryCommandSupport() {}

    static RunState.InventoryEntry findItemEntry(GameState state, String rawItemId) {
        if (rawItemId == null || rawItemId.isBlank()) {
            return null;
        }
        String normalized = rawItemId.trim();
        for (RunState.InventoryEntry entry : state.runState().inventory().items()) {
            if (entry.ref() instanceof ItemRef itemRef && normalized.equals(itemRef.itemId())) {
                return entry;
            }
        }
        return null;
    }

    static RunState.InventoryEntry findEquipEntry(GameState state, String rawEquipId) {
        if (rawEquipId == null || rawEquipId.isBlank()) {
            return null;
        }
        String normalized = rawEquipId.trim();
        for (RunState.InventoryEntry entry : state.runState().inventory().items()) {
            if (entry.ref() instanceof EquipRef equipRef && normalized.equals(equipRef.equipId())) {
                return entry;
            }
        }
        return null;
    }

    static void consumeInventoryEntry(GameState state, RunState.InventoryEntry usedEntry, int usedCount) {
        List<RunState.InventoryEntry> nextItems = new ArrayList<>();
        for (RunState.InventoryEntry entry : state.runState().inventory().items()) {
            if (!entry.ref().equals(usedEntry.ref()) || entry.bound() != usedEntry.bound()) {
                nextItems.add(entry);
                continue;
            }

            int remain = Math.max(0, entry.count() - usedCount);
            if (remain > 0) {
                nextItems.add(new RunState.InventoryEntry(entry.ref(), remain, entry.bound()));
            }
        }
        state.runState().inventory().replaceItems(nextItems);
    }

    static void addInventoryEntryCount(GameState state, InventoryEntryRef ref, boolean bound, int gainedCount) {
        if (ref == null || gainedCount <= 0) {
            return;
        }
        boolean merged = false;
        List<RunState.InventoryEntry> nextItems = new ArrayList<>();
        for (RunState.InventoryEntry entry : state.runState().inventory().items()) {
            if (!entry.ref().equals(ref) || entry.bound() != bound) {
                nextItems.add(entry);
                continue;
            }
            nextItems.add(new RunState.InventoryEntry(entry.ref(), entry.count() + gainedCount, entry.bound()));
            merged = true;
        }
        if (!merged) {
            nextItems.add(new RunState.InventoryEntry(ref, gainedCount, bound));
        }
        state.runState().inventory().replaceItems(nextItems);
    }
}
