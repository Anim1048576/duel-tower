package com.example.dueltower.engine.command;

import com.example.dueltower.engine.core.EngineContext;
import com.example.dueltower.engine.model.EquipAmmoPolicy;
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

    static RunState.InventoryEntry findEquipEntryByInventoryId(GameState state, String rawInventoryEquipId) {
        if (rawInventoryEquipId == null || rawInventoryEquipId.isBlank()) {
            return null;
        }
        String normalized = rawInventoryEquipId.trim();
        for (RunState.InventoryEntry entry : state.runState().inventory().items()) {
            if (entry.ref() instanceof EquipRef && normalized.equals(entry.inventoryEquipId())) {
                return entry;
            }
        }
        return null;
    }

    static void consumeInventoryEntry(GameState state, RunState.InventoryEntry usedEntry, int usedCount) {
        List<RunState.InventoryEntry> nextItems = new ArrayList<>();
        boolean consumed = false;
        for (RunState.InventoryEntry entry : state.runState().inventory().items()) {
            boolean sameEquipInstance = entry.inventoryEquipId() != null && entry.inventoryEquipId().equals(usedEntry.inventoryEquipId());
            if (!sameEquipInstance && (!entry.ref().equals(usedEntry.ref()) || entry.bound() != usedEntry.bound() || consumed)) {
                nextItems.add(entry);
                continue;
            }

            int remain = Math.max(0, entry.count() - usedCount);
            if (remain > 0) {
                nextItems.add(RunState.InventoryEntry.item((ItemRef) entry.ref(), remain, entry.bound()));
            }
            consumed = true;
        }
        state.runState().inventory().replaceItems(nextItems);
    }

    static void addInventoryEntryCount(GameState state, InventoryEntryRef ref, boolean bound, int gainedCount) {
        addInventoryEntryCount(state, null, ref, bound, gainedCount);
    }

    static void addInventoryEntryCount(GameState state, EngineContext ctx, InventoryEntryRef ref, boolean bound, int gainedCount) {
        if (ref == null || gainedCount <= 0) {
            return;
        }
        if (ref instanceof EquipRef equipRef) {
            addEquipInventoryEntries(state, ctx, equipRef, bound, gainedCount);
            return;
        }
        boolean merged = false;
        List<RunState.InventoryEntry> nextItems = new ArrayList<>();
        for (RunState.InventoryEntry entry : state.runState().inventory().items()) {
            if (!entry.ref().equals(ref) || entry.bound() != bound) {
                nextItems.add(entry);
                continue;
            }
            nextItems.add(RunState.InventoryEntry.item((ItemRef) entry.ref(), entry.count() + gainedCount, entry.bound()));
            merged = true;
        }
        if (!merged) {
            nextItems.add(RunState.InventoryEntry.item((ItemRef) ref, gainedCount, bound));
        }
        state.runState().inventory().replaceItems(nextItems);
    }

    static void addEquipInventoryEntry(GameState state, String inventoryEquipId, String equipId, boolean bound, Integer loadedAmmo, Integer maxLoadedAmmo) {
        List<RunState.InventoryEntry> nextItems = new ArrayList<>(state.runState().inventory().items());
        nextItems.add(RunState.InventoryEntry.equip(inventoryEquipId, new EquipRef(equipId), bound, loadedAmmo, maxLoadedAmmo));
        state.runState().inventory().replaceItems(nextItems);
    }

    private static void addEquipInventoryEntries(GameState state, EngineContext ctx, EquipRef equipRef, boolean bound, int gainedCount) {
        List<RunState.InventoryEntry> nextItems = new ArrayList<>(state.runState().inventory().items());
        for (int i = 0; i < gainedCount; i++) {
            String inventoryEquipId = java.util.UUID.randomUUID().toString();
            EquipAmmoPolicy ammoPolicy = null;
            if (ctx != null && ctx.hasEquipDef(equipRef.equipId())) {
                ammoPolicy = ctx.equipDef(equipRef.equipId()).ammoPolicy();
            }
            Integer loadedAmmo = ammoPolicy == null ? null : ammoPolicy.initialLoadedAmmo();
            Integer maxLoadedAmmo = ammoPolicy == null ? null : ammoPolicy.maxLoadedAmmo();
            nextItems.add(RunState.InventoryEntry.equip(inventoryEquipId, equipRef, bound, loadedAmmo, maxLoadedAmmo));
        }
        state.runState().inventory().replaceItems(nextItems);
    }
}
