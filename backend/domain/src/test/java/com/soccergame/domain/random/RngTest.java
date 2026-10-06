package com.soccergame.domain.random;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RngTest {

    @Test
    void sameSeedSameSequence() {
        Rng a = new Rng(42);
        Rng b = new Rng(42);
        for (int i = 0; i < 1000; i++) {
            assertThat(a.nextLong()).isEqualTo(b.nextLong());
        }
    }

    @Test
    void gameAndChoiceStreamsAreIndependent() {
        RandomStreams streams = RandomStreams.forSeed(7);
        assertThat(streams.game().nextLong()).isNotEqualTo(streams.choice().nextLong());
        // 선택 흐름을 아무리 써도 게임 흐름은 그대로다
        RandomStreams other = RandomStreams.forSeed(7);
        for (int i = 0; i < 100; i++) {
            other.choice().nextLong();
        }
        assertThat(RandomStreams.forSeed(7).game().nextLong()).isEqualTo(other.game().nextLong());
    }

    @Test
    void betweenIsInclusiveAndCoversRange() {
        Rng rng = new Rng(1);
        int[] counts = new int[21];
        for (int i = 0; i < 21_000; i++) {
            int v = rng.between(15, 35);
            assertThat(v).isBetween(15, 35);
            counts[v - 15]++;
        }
        for (int c : counts) {
            assertThat(c).isBetween(800, 1200);
        }
    }

    @Test
    void poissonMeanMatchesLambda() {
        Rng rng = new Rng(3);
        double sum = 0;
        int n = 50_000;
        for (int i = 0; i < n; i++) {
            sum += rng.poisson(1.5);
        }
        assertThat(sum / n).isBetween(1.47, 1.53);
    }
}
