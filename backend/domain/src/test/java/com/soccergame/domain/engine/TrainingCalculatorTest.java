package com.soccergame.domain.engine;

import com.soccergame.domain.TestConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TrainingCalculatorTest {
    private final TrainingCalculator calc = new TrainingCalculator(TestConfig.load());

    @Test
    void baseGrowthForNonPrimaryStat() {
        assertThat(calc.growth("finishing", 30, 0.15, 1.0, 1.0)).isCloseTo(0.15, within(1e-9));
        assertThat(calc.growth("finishing", 30, 0.10, 1.0, 1.0)).isCloseTo(0.10, within(1e-9));
    }

    @Test
    void primaryStatGetsArchetypeBonus() {
        assertThat(calc.growth("shotPower", 30, 0.15, 1.0, 1.0)).isCloseTo(0.18, within(1e-9));
        assertThat(calc.growth("heading", 30, 0.15, 1.0, 1.0)).isCloseTo(0.18, within(1e-9));
        assertThat(calc.growth("kickPower", 30, 0.15, 1.0, 1.0)).isCloseTo(0.18, within(1e-9));
    }

    @Test
    void decayBands() {
        assertThat(calc.decay(59.99)).isEqualTo(1.0);
        assertThat(calc.decay(60)).isEqualTo(0.7);
        assertThat(calc.decay(79.99)).isEqualTo(0.7);
        assertThat(calc.decay(80)).isEqualTo(0.4);
        assertThat(calc.decay(100)).isEqualTo(0.4);
    }

    @Test
    void conditionAndMenuMultiplier() {
        // 체력 메뉴(2배) × 매우 좋음(1.10) × 감쇠 0.7
        assertThat(calc.growth("fitness", 65, 0.15, 2.0, 1.10)).isCloseTo(0.15 * 2 * 1.10 * 0.7, within(1e-9));
        // 주력 × 매우 나쁨 × 감쇠 0.4
        assertThat(calc.growth("kickPower", 85, 0.10, 1.0, 0.90)).isCloseTo(0.10 * 1.2 * 0.90 * 0.4, within(1e-9));
    }
}
