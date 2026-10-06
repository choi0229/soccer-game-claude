package com.soccergame.domain.match;

import com.soccergame.domain.TestConfig;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.model.MatchRole;
import com.soccergame.domain.model.Stats;
import com.soccergame.domain.random.Rng;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class MatchEngineTest {
    private final GameConfig config = TestConfig.load();
    private final MatchEngine engine = new MatchEngine(config);

    private Stats stats(double trained, double passive) {
        Stats s = new Stats(0, 100);
        config.trainedStatKeys().forEach(k -> s.set(k, trained));
        config.passiveStatKeys().forEach(k -> s.set(k, passive));
        return s;
    }

    private MatchEngine.Input input(Stats stats, MatchRole role, double opponent, boolean knockout) {
        return new MatchEngine.Input(stats, role, 0, 40, opponent, "vsFighter", "파이터형", knockout);
    }

    @Test
    void selectionThresholds() {
        Stats s = stats(27.5, 40);
        double score = engine.selectionScore(30, s);
        assertThat(score).isCloseTo(28.75, within(1e-9));
        assertThat(engine.role(score, 40, false)).isEqualTo(MatchRole.SUB);
        assertThat(engine.role(35, 40, false)).isEqualTo(MatchRole.STARTER);
        assertThat(engine.role(34.99, 40, false)).isEqualTo(MatchRole.SUB);
        assertThat(engine.role(20, 40, false)).isEqualTo(MatchRole.SUB);
        assertThat(engine.role(19.99, 40, false)).isEqualTo(MatchRole.BENCH);
        assertThat(engine.role(100, 40, true)).isEqualTo(MatchRole.ABSENT);
    }

    @Test
    void expectedGoalsAreClamped() {
        assertThat(engine.expectedGoals(40, 40)).isCloseTo(1.2, within(1e-9));
        assertThat(engine.expectedGoals(80, 40)).isCloseTo(2.4, within(1e-9));
        assertThat(engine.expectedGoals(40, 80)).isCloseTo(0.2, within(1e-9));
        assertThat(engine.expectedGoals(200, 0)).isCloseTo(3.5, within(1e-9));
    }

    @Test
    void passiveModifierRange() {
        assertThat(engine.passiveModifier(50)).isZero();
        assertThat(engine.passiveModifier(35)).isCloseTo(-3, within(1e-9));
        assertThat(engine.passiveModifier(100)).isEqualTo(10);
        assertThat(engine.passiveModifier(0)).isEqualTo(-10);
    }

    @Test
    void benchHasNoScenes() {
        MatchEngine.Result r = engine.play(new Rng(1), input(stats(30, 40), MatchRole.BENCH, 40, false));
        assertThat(r.scenes()).isEmpty();
        assertThat(r.ourScore()).isEqualTo(r.teamGoals());
    }

    @Test
    void sceneCountsByRole() {
        for (long seed = 0; seed < 500; seed++) {
            MatchEngine.Result starter = engine.play(new Rng(seed), input(stats(30, 40), MatchRole.STARTER, 40, false));
            long base = starter.scenes().stream().filter(x -> !x.extra()).count();
            assertThat(base).isBetween(4L, 5L);
            MatchEngine.Result sub = engine.play(new Rng(seed), input(stats(30, 40), MatchRole.SUB, 40, false));
            assertThat(sub.scenes().stream().filter(x -> !x.extra()).count()).isBetween(1L, 2L);
        }
    }

    @Test
    void runInBehindAddsOneOnOneOnlyOnSuccessAndNeverChains() {
        int extras = 0;
        for (long seed = 0; seed < 2000; seed++) {
            List<SceneLog> logs = engine.play(new Rng(seed), input(stats(60, 50), MatchRole.STARTER, 40, false)).scenes();
            for (int i = 0; i < logs.size(); i++) {
                SceneLog log = logs.get(i);
                if (log.extra()) {
                    extras++;
                    assertThat(log.sceneId()).isEqualTo("ONE_ON_ONE");
                    SceneLog parent = logs.get(i - 1);
                    assertThat(parent.sceneId()).isEqualTo("RUN_IN_BEHIND");
                    assertThat(parent.success()).isTrue();
                    assertThat(log.minute()).isEqualTo(parent.minute());
                } else if (log.sceneId().equals("RUN_IN_BEHIND") && log.success()) {
                    assertThat(logs.get(i + 1).extra()).isTrue();
                }
            }
        }
        assertThat(extras).isPositive();
    }

    @Test
    void probabilityFormulaAndTakeOnBonus() {
        // 패시브 50 → 상황 보정 0. 기세 0 이어도 0 미만은 0 처리. 능력치 50, 상대 40, 컨디션 0.
        // 골 장면 25 + 8 = 33 (돌파 보너스면 48), 그 밖 50 + 8 = 58
        Stats s = stats(50, 50);
        s.set("momentum", 0);
        int bonusSeen = 0;
        for (long seed = 0; seed < 2000; seed++) {
            List<SceneLog> logs = engine.play(new Rng(seed), input(s, MatchRole.STARTER, 40, false)).scenes();
            boolean pending = false;
            for (SceneLog log : logs) {
                boolean goalScene = config.scene(log.sceneId()).isGoalScene();
                double expected = goalScene ? (pending ? 48 : 33) : 58;
                assertThat(log.probability()).as("seed %d %s", seed, log.sceneId()).isEqualTo(expected);
                if (goalScene && pending) {
                    bonusSeen++;
                    pending = false;
                }
                if (log.sceneId().equals("TAKE_ON") && log.success()) {
                    pending = true;
                }
            }
        }
        assertThat(bonusSeen).isPositive();
    }

    @Test
    void probabilityIsClampedAndPassiveTotalCapped() {
        for (long seed = 0; seed < 300; seed++) {
            for (SceneLog log : engine.play(new Rng(seed), input(stats(100, 100), MatchRole.STARTER, 0, false)).scenes()) {
                assertThat(log.probability()).isEqualTo(95);
            }
            for (SceneLog log : engine.play(new Rng(seed), input(stats(0, 0), MatchRole.STARTER, 80, false)).scenes()) {
                assertThat(log.probability()).isEqualTo(5);
            }
            // 패시브 100: 유형 대응 +10 만으로 이미 상한. 다른 상황이 겹쳐도 합계는 +10
            for (SceneLog log : engine.play(new Rng(seed), input(stats(40, 100), MatchRole.STARTER, 40, false)).scenes()) {
                boolean goal = config.scene(log.sceneId()).isGoalScene();
                double base = goal ? 25 : 50;
                assertThat(log.probability()).isIn(base + 10, base + 10 + 15);
            }
        }
    }

    @Test
    void finalScoreAddsPlayerContributions() {
        for (long seed = 0; seed < 500; seed++) {
            MatchEngine.Result r = engine.play(new Rng(seed), input(stats(70, 60), MatchRole.STARTER, 40, false));
            assertThat(r.ourScore()).isEqualTo(r.teamGoals() + r.playerGoals() + r.playerAssists() + r.pressGoals());
            assertThat(r.penaltyWin()).isNull();
            TimelineEntry last = r.timeline().getLast();
            assertThat(last.ourScore()).isEqualTo(r.ourScore());
            assertThat(last.theirScore()).isEqualTo(r.theirScore());
        }
    }

    @Test
    void knockoutDrawsGoToPenalties() {
        int draws = 0;
        int wins = 0;
        for (long seed = 0; seed < 2000; seed++) {
            MatchEngine.Result r = engine.play(new Rng(seed), input(stats(30, 40), MatchRole.BENCH, 40, true));
            if (r.ourScore() == r.theirScore()) {
                draws++;
                assertThat(r.penaltyWin()).isNotNull();
                if (r.penaltyWin()) {
                    wins++;
                }
            } else {
                assertThat(r.penaltyWin()).isNull();
            }
        }
        assertThat(draws).isPositive();
        assertThat((double) wins / draws).isBetween(0.4, 0.6);
    }

    @Test
    void ratingFormula() {
        // 골 1, 도움 1, 그 밖 성공 1, 실패 2 → 6 + 1 + 0.7 + 0.3 - 0.4 = 7.6
        MatchEngine.Result r = new MatchEngine.Result(0, 0, 1, 1, 0, 2, 0, null, List.of(), List.of(), 3, 2);
        assertThat(engine.rating(r)).isEqualTo(7.6);
        MatchEngine.Result bad = new MatchEngine.Result(0, 0, 0, 0, 0, 0, 0, null, List.of(), List.of(), 0, 20);
        assertThat(engine.rating(bad)).isEqualTo(3.0);
        MatchEngine.Result great = new MatchEngine.Result(0, 0, 6, 0, 0, 6, 0, null, List.of(), List.of(), 6, 0);
        assertThat(engine.rating(great)).isEqualTo(10.0);
    }

    @Test
    void sameRngSameMatch() {
        MatchEngine.Result a = engine.play(new Rng(5), input(stats(45, 45), MatchRole.STARTER, 50, true));
        MatchEngine.Result b = engine.play(new Rng(5), input(stats(45, 45), MatchRole.STARTER, 50, true));
        assertThat(a).isEqualTo(b);
    }
}
