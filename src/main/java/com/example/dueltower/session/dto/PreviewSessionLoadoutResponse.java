package com.example.dueltower.session.dto;

import com.example.dueltower.screen.dto.PlayerLobbyDeckEditorStateDto;

public record PreviewSessionLoadoutResponse(
        PreviewSessionLoadoutDraftDto draft,
        String draftSignature,
        PlayerLobbyDeckEditorStateDto deckEditor
) {}
