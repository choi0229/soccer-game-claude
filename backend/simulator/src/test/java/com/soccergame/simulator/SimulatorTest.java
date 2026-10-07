package com.soccergame.simulator;

import com.soccergame.domain.config.ConfigLoader;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.engine.GameEngine;
import com.soccergame.domain.engine.GameState;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SimulatorTest {
    private final GameConfig config = ConfigLoader.load(Path.of(System.getProperty("game.config.dir")));
    private final YearRunner runner = new YearRunner(new GameEngine(config));

    @Test
    void everyStrategyFinishesTheYearDeterministically() {
        for (Strategy strategy : Strategies.all()) {
            GameState a = runner.run(42, strategy);
            GameState b = runner.run(42, strategy);
            assertThat(a.finished).isTrue();
            assertThat(a.stats.asMap()).isEqualTo(b.stats.asMap());
            assertThat(a.matches).isEqualTo(b.matches);
            assertThat(a.eventHistory).isEqualTo(b.eventHistory);
        }
    }

    @Test
    void reportRendersAllSections() {
        String report = SimulatorMain.run(config, 5, 1);
        assertThat(report).contains("무작위", "훈련 위주", "균형", "## 1.", "## 5.", "리그 8위");
    }

    @Test
    void growthMaxTakesContactAndPrefersHardWorkerOrCompetitor() {
        GameEngine engine = new GameEngine(config);
        GameState s = engine.newGame(1);
        Strategies.GrowthMax strategy = new Strategies.GrowthMax();
        var intro = config.event("friend_007").orElseThrow();
        assertThat(strategy.eventChoice(s, intro, new com.soccergame.domain.random.Rng(1))).isZero();
        var firstMeeting = config.event("coach_001").orElseThrow(); // 0 승부사, 1 팀플레이어, 2 없음
        for (long seed = 0; seed < 20; seed++) {
            assertThat(strategy.eventChoice(s, firstMeeting, new com.soccergame.domain.random.Rng(seed))).isZero();
        }
    }

    @Test
    void balancedAddsGirlfriendToSundayRotationOnceUnlocked() {
        GameEngine engine = new GameEngine(config);
        GameState s = engine.newGame(1);
        Strategies.Balanced balanced = new Strategies.Balanced();
        java.util.Set<com.soccergame.domain.model.Axis> met = new java.util.HashSet<>();
        for (int w = 0; w < 10; w++) {
            s.week = w;
            var a = balanced.sunday(s, engine, null);
            if (a.meetTarget() != null) met.add(a.meetTarget());
        }
        assertThat(met).doesNotContain(com.soccergame.domain.model.Axis.GIRLFRIEND);
        s.affinity.put(com.soccergame.domain.model.Axis.GIRLFRIEND, 30.0);
        for (int w = 0; w < 10; w++) {
            s.week = w;
            var a = balanced.sunday(s, engine, null);
            if (a.meetTarget() != null) met.add(a.meetTarget());
        }
        assertThat(met).contains(com.soccergame.domain.model.Axis.GIRLFRIEND);
    }

    @Test
    void randomStrategyTraitScoresAreWithinTwentyPercent() {
        java.util.Map<String, Double> sum = new java.util.LinkedHashMap<>();
        int n = 300;
        for (long seed = 1; seed <= n; seed++) {
            GameState s = runner.run(seed, Strategies.all().getFirst());
            s.traitScores.forEach((k, v) -> sum.merge(k, (double) v, Double::sum));
        }
        double max = sum.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        double min = sum.values().stream().mapToDouble(Double::doubleValue).min().orElseThrow();
        assertThat(sum).hasSize(4);
        assertThat(max / min).as("특성 연간 평균 %s", sum).isLessThanOrEqualTo(1.2);
    }

    @Test
    void trainingStrategiesPickTheSafestClutchOption() {
        var options = java.util.List.of(
                new com.soccergame.domain.match.ClutchOption(0, "a", null, java.util.List.of(), 12, java.util.List.of(), "", -0.4, null),
                new com.soccergame.domain.match.ClutchOption(1, "b", null, java.util.List.of(), 61, java.util.List.of(), "", -0.1, null),
                new com.soccergame.domain.match.ClutchOption(2, "c", null, java.util.List.of(), 40, java.util.List.of(), "", -0.2, null));
        for (Strategy st : Strategies.all()) {
            if (st.name().equals("무작위") || st.name().equals("균형")) {
                continue;
            }
            assertThat(st.clutchChoice(null, options, new com.soccergame.domain.random.Rng(1))).as(st.name()).isEqualTo(1);
        }
    }
}
