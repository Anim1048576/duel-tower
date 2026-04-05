package com.example.dueltower.engine.model;

public record EquippedItem(
        String inventoryEquipId,
        String equipId,
        boolean bound,
        Integer loadedAmmo,
        Integer maxLoadedAmmo
) {
}
