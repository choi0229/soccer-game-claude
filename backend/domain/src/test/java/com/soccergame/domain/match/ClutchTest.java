package com.soccergame.domain.match;

import com.soccergame.domain.TestConfig;
import com.soccergame.domain.config.ClutchMoments;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.model.MatchRole;
import com.soccergame.domain.model.Stats;
import com.soccergame.domain.random.Rng;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ClutchTest {
    private final GameConfig config = TestConfig.load();
    private final MatchEngine engine = new MatchEngine(config);

    private Stats stats(double trained, double passive) {
        Stats s = new Stats(0, 100);
        config.trainedStatKeys().forEach(k -> s.set(k, trained));
        config.passiveStatKeys().forEach(k -> s.set(k, passive));
        return s;
    }

    private MatchEngine.Input input(MatchRole role) {
        return new MatchEngine.Input(stats(45, 45), role, 0, 40, 50, "vsFighter", "파이터형", false, true);
    }

    @Test
    void clutchRateByRole() {
        int starter = 0;
        int sub = 0;
        int n = 3000;
        for (long seed = 0; seed < n; seed++) {
            if (engine.start(new Rng(seed), input(MatchRole.STARTER)).awaitingChoice()) starter++;
            if (engine.start(new Rng(seed), input(MatchRole.SUB)).awaitingChoice()) sub++;
            assertThat(engine.start(new Rng(seed), input(MatchRole.BENCH)).awaitingChoice()).isFalse();
        }
        assertThat((double) starter / n).isBetween(0.56, 0.64);
        assertThat((double) sub / n).isBetween(0.27, 0.33);
    }

    @Test
    void clutchReplacesOneBaseSceneAndMatchesSituation() {
        int checked = 0;
        int[] slotCounts = new int[5];
        int scenesAfterClutch = 0;
        for (long seed = 0; seed < 1000; seed++) {
            MatchSession s = engine.start(new Rng(seed), input(MatchRole.STARTER));
            if (!s.awaitingChoice()) {
                continue;
            }
            checked++;
            int baseScenes = s.picked.size();
            // 멈춘 시점은 대체한 기본 장면(clutchSlot)의 분
            assertThat(s.momentMinute()).isEqualTo((s.clutchSlot + 1) * 80 / (baseScenes + 1));
            if (baseScenes == 5) {
                slotCounts[s.clutchSlot]++;
            }
            List<TimelineEntry> partial = engine.partialTimeline(s);
            TimelineEntry last = partial.getLast();
            ClutchMoments.Condition c = s.moment().condition();
            assertThat(c.half()).isEqualTo(s.momentMinute() <= 40 ? ClutchMoments.Half.FIRST : ClutchMoments.Half.SECOND);
            ClutchMoments.ScoreState expected = last.ourScore() > last.theirScore() ? ClutchMoments.ScoreState.LEADING
                    : last.ourScore() < last.theirScore() ? ClutchMoments.ScoreState.TRAILING : ClutchMoments.ScoreState.TIED;
            assertThat(c.score()).isEqualTo(expected);

            engine.resolveClutch(new Rng(seed + 1), s, 0);
            MatchEngine.Result r = s.result();
            long nonExtraBase = r.scenes().stream().filter(x -> !x.extra() && !x.sceneId().equals("CLUTCH")).count();
            assertThat(nonExtraBase).isEqualTo(baseScenes - 1);
            assertThat(r.scenes().stream().filter(x -> x.sceneId().equals("CLUTCH"))).hasSize(1);
            assertThat(r.clutch()).isNotNull();
            // 진행 중 중계는 최종 중계의 앞부분과 같다
            assertThat(r.timeline().subList(0, partial.size())).isEqualTo(partial);
            assertThat(r.timeline().get(partial.size()).kind()).isEqualTo("CLUTCH");
            // 승부처 뒤에 남은 기본 장면은 평소대로 판정된다
            long after = r.scenes().stream().filter(x -> !x.extra() && x.minute() > s.momentMinute()).count();
            assertThat(after).isEqualTo(baseScenes - 1 - s.clutchSlot);
            scenesAfterClutch += after;
        }
        assertThat(checked).isGreaterThan(400);
        assertThat(scenesAfterClutch).isPositive();
        // 장면 5개짜리 경기에서 대체 위치는 다섯 곳에 고르게 나온다
        int total = java.util.Arrays.stream(slotCounts).sum();
        for (int c : slotCounts) {
            assertThat((double) c / total).isBetween(0.12, 0.28);
        }
    }

    @Test
    void previewProbabilityIsTheJudgedProbability() {
        for (long seed = 0; seed < 300; seed++) {
            MatchSession s = engine.start(new Rng(seed), input(MatchRole.STARTER));
            if (!s.awaitingChoice()) {
                continue;
            }
            List<ClutchOption> options = engine.clutchOptions(s);
            int pick = (int) (seed % options.size());
            engine.resolveClutch(new Rng(seed), s, pick);
            assertThat(s.result().clutch().probability()).isEqualTo(options.get(pick).probability());
            assertThat(s.result().clutch().style()).isEqualTo(options.get(pick).style());
        }
    }

    @Test
    void sameSeedSameChoiceSameResult() {
        for (long seed = 0; seed < 200; seed++) {
            Rng a = new Rng(seed);
            Rng b = new Rng(seed);
            MatchSession sa = engine.start(a, input(MatchRole.STARTER));
            MatchSession sb = engine.start(b, input(MatchRole.STARTER));
            if (sa.awaitingChoice()) {
                engine.resolveClutch(a, sa, 1);
                engine.resolveClutch(b, sb, 1);
            }
            assertThat(sa.result()).isEqualTo(sb.result());
        }
    }

    @Test
    void failedClutchUsesChoiceFailRating() {
        MatchEngine.Result r = new MatchEngine.Result(0, 0, 0, 0, 0, 0, 0, 0, null, List.of(), List.of(), 0, 0, 0,
                -0.4, null);
        assertThat(engine.rating(r)).isEqualTo(5.6);
    }

    @Test
    void momentsAreRiskRewardOrdered() {
        Map<ClutchMoments.Outcome, Integer> rank = Map.of(ClutchMoments.Outcome.GOAL, 4,
                ClutchMoments.Outcome.ASSIST, 3, ClutchMoments.Outcome.EXTRA_SHOT, 2,
                ClutchMoments.Outcome.TEAM_GOAL_CHANCE, 1);
        List<ClutchMoments.MomentDef> moments = config.clutchMoments().moments();
        assertThat(moments).hasSize(10);
        for (ClutchMoments.MomentDef m : moments) {
            List<ClutchMoments.MomentChoice> byBase = m.choices().stream()
                    .sorted(java.util.Comparator.comparingDouble(ClutchMoments.MomentChoice::base)).toList();
            for (int i = 1; i < byBase.size(); i++) {
                ClutchMoments.MomentChoice riskier = byBase.get(i - 1);
                ClutchMoments.MomentChoice safer = byBase.get(i);
                assertThat(rank.get(riskier.success().outcome())).as(m.id())
                        .isGreaterThan(rank.get(safer.success().outcome()));
                assertThat(riskier.failRating()).as(m.id()).isLessThanOrEqualTo(safer.failRating());
            }
        }
        // 전반/후반 × 스코어 6가지 상황마다 1~2건
        for (ClutchMoments.Half h : ClutchMoments.Half.values()) {
            for (ClutchMoments.ScoreState sc : ClutchMoments.ScoreState.values()) {
                long n = moments.stream().filter(m -> m.condition().half() == h && m.condition().score() == sc).count();
                assertThat(n).as(h + " " + sc).isBetween(1L, 2L);
            }
        }
    }
}
