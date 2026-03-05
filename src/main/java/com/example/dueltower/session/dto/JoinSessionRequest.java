package com.example.dueltower.session.dto;

import java.util.List;

public record JoinSessionRequest(
        String playerId,
        List<String> passiveIds
) {}
