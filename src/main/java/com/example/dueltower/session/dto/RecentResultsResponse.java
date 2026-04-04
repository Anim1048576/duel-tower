package com.example.dueltower.session.dto;

import java.util.List;

public record RecentResultsResponse(
        long version,
        boolean resultPending,
        RunStateDto.CurrentNodeDto currentNode,
        List<RunStateDto.RecentResultDto> recentResults
) {
}
