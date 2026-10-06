package com.soccergame.simulator;

import java.util.Arrays;

/** 표본 분포 요약: 평균, 표준편차, 최소, 10/50/90 백분위, 최대 */
public record Distribution(int n, double mean, double sd, double min, double p10, double p50, double p90, double max) {

    public static Distribution of(double[] values) {
        if (values.length == 0) {
            return new Distribution(0, 0, 0, 0, 0, 0, 0, 0);
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double mean = Arrays.stream(sorted).average().orElse(0);
        double var = Arrays.stream(sorted).map(v -> (v - mean) * (v - mean)).sum() / sorted.length;
        return new Distribution(sorted.length, mean, Math.sqrt(var), sorted[0], percentile(sorted, 0.10),
                percentile(sorted, 0.50), percentile(sorted, 0.90), sorted[sorted.length - 1]);
    }

    private static double percentile(double[] sorted, double q) {
        int index = (int) Math.ceil(q * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }
}
