package com.example.dueltower.session.dto;

import java.util.List;

public record SessionRunChoicesResponse(
        long version,
        boolean resultPending,
        RunStateDto.CurrentNodeDto currentNode,
        List<RunStateDto.NodeChoiceDto> availableChoices
) {
}
