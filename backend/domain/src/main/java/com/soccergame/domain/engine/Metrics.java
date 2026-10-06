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
    /** 메뉴 훈련 횟수, 그중 보너스 합계, 상한에 걸린 횟수 */
    public int menuTrainings;
    public double bonusApplied;
    public int bonusCapped;
    /** 실제로 나머지 공부를 한 날 수 */
    public int makeupDays;

    public Metrics(int weeks) {
        staminaSum = new double[weeks];
        staminaSamples = new int[weeks];
    }

    public double averageStamina(int week) {
        return staminaSamples[week] == 0 ? 0 : staminaSum[week] / staminaSamples[week];
    }
}
