package com.example.dueltower.session.dto;

import java.util.List;

public record SessionShopResponse(
        String sessionCode,
        long version,
        boolean open,
        String unavailableReason,
        int gold,
        List<ShopOfferDto> offers
) {
}
