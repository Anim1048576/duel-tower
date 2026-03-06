package com.example.dueltower.session.dto;

import java.util.List;

public record UpdateSessionDeckRequest(
        List<String> deckCardIds
) {}
