package com.example.dueltower.session.dto;

import java.util.List;

public record ShopOfferDto(
        String offerId,
        String entryType,
        String refId,
        String name,
        int price,
        int stock,
        boolean bound,
        boolean battleUsable,
        String summary,
        String description,
        List<String> tags,
        Integer loadedAmmo,
        Integer maxLoadedAmmo
) {
}
