package com.example.dueltower.engine.command;

import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.RunState;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InventoryCommandSupportTest {

    @Test
    void addInventoryItemCountShouldMergeSameItemIdAndBound() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 10L);

        RunState.InventoryItem before = InventoryCommandSupport.findInventoryItem(state, "I-1");
        assertNotNull(before);

        InventoryCommandSupport.addInventoryItemCount(state, new RunState.InventoryItem("I-1", 1, false), 2);

        RunState.InventoryItem after = InventoryCommandSupport.findInventoryItem(state, "I-1");
        assertNotNull(after);
        assertEquals(before.count() + 2, after.count());
        assertFalse(after.bound());
    }

    @Test
    void addInventoryItemCountShouldNotMergeDifferentBound() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 11L);

        InventoryCommandSupport.addInventoryItemCount(state, new RunState.InventoryItem("I-1", 1, true), 2);

        long sameIdCount = state.runState().inventory().items().stream()
                .filter(item -> "I-1".equals(item.itemId()))
                .count();

        assertEquals(2, sameIdCount);
        assertTrue(state.runState().inventory().items().stream()
                .anyMatch(item -> "I-1".equals(item.itemId()) && item.bound() && item.count() == 2));
    }

    @Test
    void consumeInventoryItemKeepsEntryWhenCountRemains() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 12L);

        RunState.InventoryItem item = InventoryCommandSupport.findInventoryItem(state, "I-1");
        assertNotNull(item);

        InventoryCommandSupport.consumeInventoryItem(state, item, 1);

        RunState.InventoryItem remained = InventoryCommandSupport.findInventoryItem(state, "I-1");
        assertNotNull(remained);
        assertEquals(item.count() - 1, remained.count());
    }

    @Test
    void consumeInventoryItemRemovesEntryWhenCountBecomesZero() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 13L);

        RunState.InventoryItem item = state.runState().inventory().items().stream()
                .filter(it -> "I-2".equals(it.itemId()))
                .findFirst()
                .orElseThrow();

        InventoryCommandSupport.consumeInventoryItem(state, item, item.count());

        assertTrue(state.runState().inventory().items().stream()
                .noneMatch(it -> "I-2".equals(it.itemId()) && it.bound() == item.bound()));
    }

    @Test
    void findInventoryItemReturnsNullForUnknownId() {
        GameState state = new GameState(new Ids.SessionId(UUID.randomUUID()), 14L);

        RunState.InventoryItem item = InventoryCommandSupport.findInventoryItem(state, "__UNKNOWN_ITEM__");

        assertNull(item);
    }
}
