package com.soccergame.domain.random;

import java.util.List;

/**
 * SplitMix64 기반 결정적 난수. 상태가 long 하나라서 같은 시드와 같은 호출 순서는 항상 같은 값을 낸다.
 */
public final class Rng {
    private long state;

    public Rng(long seed) {
        this.state = seed;
    }

    public long state() {
        return state;
    }

    public long nextLong() {
        long z = (state += 0x9E3779B97F4A7C15L);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** [0, 1) */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** [0, bound) 균등 정수 */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive: " + bound);
        }
        // 63비트 양수에서 나머지를 취하고, 마지막 불완전 구간은 버린다 (java.util.Random 과 같은 방식)
        long u = nextLong() >>> 1;
        long r = u % bound;
        while (u - r + (bound - 1) < 0) {
            u = nextLong() >>> 1;
            r = u % bound;
        }
        return (int) r;
    }

    /** [min, max] 균등 정수 */
    public int between(int min, int max) {
        return min + nextInt(max - min + 1);
    }

    public boolean chance(double probability) {
        return nextDouble() < probability;
    }

    public <T> T pick(List<T> items) {
        return items.get(nextInt(items.size()));
    }

    public <T> void shuffle(List<T> items) {
        for (int i = items.size() - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            T tmp = items.get(i);
            items.set(i, items.get(j));
            items.set(j, tmp);
        }
    }

    /** Knuth 방식 포아송 표본 (λ ≤ 3.5 범위라 충분하다) */
    public int poisson(double lambda) {
        double limit = Math.exp(-lambda);
        double product = nextDouble();
        int k = 0;
        while (product > limit) {
            k++;
            product *= nextDouble();
        }
        return k;
    }
}
