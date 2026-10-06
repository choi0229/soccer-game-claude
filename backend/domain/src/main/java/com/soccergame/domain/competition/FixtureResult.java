package com.soccergame.domain.competition;

/** 한 경기의 결과. penaltyHomeWin 은 토너먼트 무승부일 때만 값이 있다. */
public record FixtureResult(int homeId, int awayId, int homeGoals, int awayGoals, Boolean penaltyHomeWin) {

    public int winnerId() {
        if (homeGoals != awayGoals) {
            return homeGoals > awayGoals ? homeId : awayId;
        }
        if (penaltyHomeWin == null) {
            return -1;
        }
        return penaltyHomeWin ? homeId : awayId;
    }
}
