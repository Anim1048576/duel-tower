package com.example.dueltower.engine.command;

import com.example.dueltower.engine.model.*;
import com.example.dueltower.engine.model.Ids;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InventoryCommandSupportTest {

    @Test
    void addInventoryEntryCountShouldMergeSameRefAndBound() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 10L);

        RunState.InventoryEntry before = InventoryCommandSupport.findItemEntry(state, "I-1");
        assertNotNull(before);

        InventoryCommandSupport.addInventoryEntryCount(state, new ItemRef("I-1"), false, 2);

        RunState.InventoryEntry after = InventoryCommandSupport.findItemEntry(state, "I-1");
        assertNotNull(after);
        assertEquals(before.count() + 2, after.count());
        assertFalse(after.bound());
    }

    @Test
    void addInventoryEntryCountShouldNotMergeDifferentBound() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 11L);

        InventoryCommandSupport.addInventoryEntryCount(state, new ItemRef("I-1"), true, 2);

        long sameIdCount = state.runState().inventory().items().stream()
                .filter(entry -> entry.ref() instanceof ItemRef itemRef && "I-1".equals(itemRef.itemId()))
                .count();

        assertEquals(2, sameIdCount);
        assertTrue(state.runState().inventory().items().stream()
                .anyMatch(entry -> entry.ref() instanceof ItemRef itemRef
                        && "I-1".equals(itemRef.itemId())
                        && entry.bound()
                        && entry.count() == 2));
    }

    @Test
    void consumeInventoryEntryKeepsEntryWhenCountRemains() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 12L);

        RunState.InventoryEntry item = InventoryCommandSupport.findItemEntry(state, "I-1");
        assertNotNull(item);

        InventoryCommandSupport.consumeInventoryEntry(state, item, 1);

        RunState.InventoryEntry remained = InventoryCommandSupport.findItemEntry(state, "I-1");
        assertNotNull(remained);
        assertEquals(item.count() - 1, remained.count());
    }

    @Test
    void consumeInventoryEntryRemovesEntryWhenCountBecomesZero() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 13L);

        RunState.InventoryEntry item = state.runState().inventory().items().stream()
                .filter(it -> it.ref() instanceof ItemRef itemRef && "I-2".equals(itemRef.itemId()))
                .findFirst()
                .orElseThrow();

        InventoryCommandSupport.consumeInventoryEntry(state, item, item.count());

        assertTrue(state.runState().inventory().items().stream()
                .noneMatch(it -> it.ref() instanceof ItemRef itemRef
                        && "I-2".equals(itemRef.itemId())
                        && it.bound() == item.bound()));
    }

    @Test
    void findItemEntryReturnsNullForUnknownId() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 14L);

        RunState.InventoryEntry item = InventoryCommandSupport.findItemEntry(state, "__UNKNOWN_ITEM__");

        assertNull(item);
    }
}
