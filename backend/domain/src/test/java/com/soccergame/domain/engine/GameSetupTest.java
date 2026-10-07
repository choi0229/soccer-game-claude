package com.soccergame.domain.engine;

import com.soccergame.domain.TestConfig;
import com.soccergame.domain.calendar.GameCalendar;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.School;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class GameSetupTest {
    private final GameConfig config = TestConfig.load();
    private final GameCalendar calendar = new GameCalendar(config.rules().calendar());
    private final GameSetup setup = new GameSetup(config, calendar, new Resources(config),
            new Competitions(config, calendar, new Resources(config),
                    new com.soccergame.domain.match.MatchEngine(config)));

    @Test
    void createsThirtyTwoSchoolsInFourBalancedRegions() {
        GameState s = setup.create(1, null);
        assertThat(s.schools).hasSize(32);
        assertThat(s.schools.stream().map(School::name).distinct()).hasSize(32);
        Map<String, Long> byType = s.schools.stream().collect(Collectors.groupingBy(School::typeKey, Collectors.counting()));
        assertThat(byType).containsEntry("PRO", 4L).containsEntry("ELITE", 4L).containsEntry("STRONG", 8L)
                .containsEntry("RISING", 8L).containsEntry("DARK_HORSE", 8L);
        for (int region = 1; region <= 4; region++) {
            int r = region;
            assertThat(s.schools.stream().filter(x -> x.region() == r)).hasSize(8);
        }
        assertThat(s.schools).allMatch(x -> x.defenderType().equals("FIGHTER") || x.defenderType().equals("COMMANDER"));
    }

    @Test
    void playerSchoolIsConfiguredType() {
        GameState s = setup.create(1, null);
        assertThat(s.playerSchool().typeKey()).isEqualTo("DARK_HORSE");
        assertThat(s.playerSchool().strength()).isEqualTo(40);
        assertThat(s.playerSchool().region()).isEqualTo(1);
    }

    @Test
    void startingStatsFollowRanges() {
        for (long seed = 0; seed < 200; seed++) {
            GameState s = setup.create(seed, null);
            for (String key : config.trainedStatKeys()) {
                double v = s.stats.get(key);
                if (config.isPrimary(key)) {
                    assertThat(v).isBetween(25.0, 45.0);
                } else {
                    assertThat(v).isBetween(15.0, 35.0);
                }
            }
            for (String key : config.passiveStatKeys()) {
                assertThat(s.stats.get(key)).isBetween(30.0, 50.0);
            }
        }
    }

    @Test
    void startingResources() {
        GameState s = setup.create(5, null);
        double fitness = s.stats.get("fitness");
        assertThat(s.stamina).isEqualTo(100 + (fitness - 50) / 2);
        assertThat(s.condition).isEqualTo(2);
        assertThat(s.academics).isEqualTo(50);
        assertThat(s.money).isZero();
        assertThat(s.reputation).isZero();
        assertThat(s.affinity).containsEntry(Axis.COACH, 30.0).containsEntry(Axis.TEAMMATE, 30.0)
                .containsEntry(Axis.FAMILY, 50.0).containsEntry(Axis.FRIEND, 30.0)
                .doesNotContainKey(Axis.GIRLFRIEND);
        assertThat(s.traitScores).containsOnlyKeys("hardWorker", "competitor", "teamPlayer", "modelStudent")
                .allSatisfy((k, v) -> assertThat(v).isZero());
        assertThat(s.menuTrainingCounts).hasSize(7).allSatisfy((k, v) -> assertThat(v).isZero());
    }

    @Test
    void sameSeedSameSetup() {
        GameState a = setup.create(99, null);
        GameState b = setup.create(99, null);
        assertThat(a.schools).isEqualTo(b.schools);
        assertThat(a.stats.asMap()).isEqualTo(b.stats.asMap());
        assertThat(a.cups.get("spring").alive()).isEqualTo(b.cups.get("spring").alive());
        // 네 권역 리그를 모두 만들고, 첫 주에 시작하는 춘계배만 추첨해 둔다
        assertThat(a.leagues).hasSize(4);
        assertThat(a.league).isSameAs(a.leagues.get(a.playerSchool().region()));
        assertThat(a.cups).containsOnlyKeys("spring");
        assertThat(a.rng.state()).isEqualTo(b.rng.state());
    }

    @Test
    void chosenSchoolKeepsEverythingElse() {
        GameState base = setup.create(42, null);
        int other = base.schools.stream().filter(x -> x.typeKey().equals("PRO")).findFirst().orElseThrow().id();
        GameState chosen = setup.create(42, other);
        assertThat(chosen.playerSchoolId).isEqualTo(other);
        assertThat(chosen.playerSchool().strength()).isEqualTo(80);
        // 학교를 바꿔도 학교 이름·능력치 시작값·대진·난수 상태는 같다
        assertThat(chosen.schools).isEqualTo(base.schools);
        assertThat(chosen.stats.asMap()).isEqualTo(base.stats.asMap());
        assertThat(chosen.cups.get("spring").alive()).isEqualTo(base.cups.get("spring").alive());
        assertThat(chosen.rng.state()).isEqualTo(base.rng.state());
        assertThat(chosen.league).isSameAs(chosen.leagues.get(chosen.playerSchool().region()));
        // 기본 학교를 직접 지정하면 지정하지 않은 것과 같다
        assertThat(setup.create(42, base.playerSchoolId).playerSchoolId).isEqualTo(base.playerSchoolId);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> setup.create(42, 99))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
