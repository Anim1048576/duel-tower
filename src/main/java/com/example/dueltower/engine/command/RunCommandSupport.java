package com.example.dueltower.engine.command;

import com.example.dueltower.engine.model.GameState;
import com.example.dueltower.engine.model.Ids;
import com.example.dueltower.engine.model.PendingDecision;
import com.example.dueltower.engine.model.RunState;

final class RunCommandSupport {
    private RunCommandSupport() {}

    static boolean isEventNodePending(GameState state) {
        return state.runState().currentNode() != null
                && !state.runState().resultPending()
                && state.runState().currentNode().phase() == RunState.NodePhase.EVENT;
    }

    static boolean isJudgementNodePending(GameState state) {
        return state.runState().currentNode() != null
                && !state.runState().resultPending()
                && state.runState().currentNode().phase() == RunState.NodePhase.JUDGEMENT;
    }

    static PendingDecision.JudgementChoice pendingJudgementChoice(GameState state, Ids.PlayerId playerId) {
        if (playerId == null || state.player(playerId) == null) {
            return null;
        }
        if (state.player(playerId).pendingDecision() instanceof PendingDecision.JudgementChoice decision) {
            return decision;
        }
        return null;
    }

    // 다음 라운드 확장 포인트: CLAIM_RECENT_RESULT(resultId/resultIndex)
    static RunState.RecentResult findRecentResultById(GameState state, String resultId) {
        if (state == null || resultId == null || resultId.isBlank()) {
            return null;
        }
        String normalized = resultId.trim();
        for (RunState.RecentResult result : state.runState().recentResults()) {
            if (normalized.equals(result.id())) {
                return result;
            }
        }
        return null;
    }

    static RunState.RecentResult findRecentResultByIndex(GameState state, Integer resultIndex) {
        if (state == null || resultIndex == null) {
            return null;
        }
        int index = resultIndex;
        if (index < 0 || index >= state.runState().recentResults().size()) {
            return null;
        }
        return state.runState().recentResults().get(index);
    }
}
