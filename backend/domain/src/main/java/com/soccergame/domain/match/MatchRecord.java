package com.soccergame.domain.match;

import com.soccergame.domain.model.Competition;
import com.soccergame.domain.model.MatchRole;

import java.util.List;

/**
 * 플레이어 학교 경기 1개의 확정 결과.
 * rating 은 출전(선발·교체)한 경기만 값이 있다.
 * penaltyWin 은 토너먼트 무승부일 때만 값이 있다.
 */
public record MatchRecord(Competition competition, String roundLabel, int week, String dateLabel,
                          int opponentId, String opponentName, double opponentStrength, String opponentDefender,
                          boolean home, MatchRole role, double selectionScore,
                          int teamGoals, int opponentGoals, int playerGoals, int playerAssists, int pressGoals,
                          int ourScore, int theirScore, Boolean penaltyWin, String result,
                          Double rating, double reputationGained, String passiveGrowth, Double passiveGrowthAmount,
                          List<SceneLog> scenes, List<TimelineEntry> timeline) {

    public boolean won() {
        return "W".equals(result);
    }
}
