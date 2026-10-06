package com.soccergame.domain.engine;

/** 시즌 요약과 시뮬레이터용 집계. 게임 판정에는 쓰지 않는다. */
public final class Metrics {
    /** 주별 하루 끝(밤 회복 전) 체력의 합과 표본 수 */
    public final double[] staminaSum;
    public final int[] staminaSamples;
    public int exclusions;
    public int injuries;
    public int injuryDaysMissed;
    public int trainingSessions;

    public Metrics(int weeks) {
        staminaSum = new double[weeks];
        staminaSamples = new int[weeks];
    }

    public double averageStamina(int week) {
        return staminaSamples[week] == 0 ? 0 : staminaSum[week] / staminaSamples[week];
    }
}
