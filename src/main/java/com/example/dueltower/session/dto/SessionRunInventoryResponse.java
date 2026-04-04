package com.example.dueltower.session.dto;

public record SessionRunInventoryResponse(
        long version,
        RunStateDto.InventoryDto inventory
) {
}
