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
    private final GameSetup setup = new GameSetup(config, new GameCalendar(config.rules().calendar()),
            new Resources(config));

    @Test
    void createsThirtyTwoSchoolsInFourBalancedRegions() {
        GameState s = setup.create(1);
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
        GameState s = setup.create(1);
        assertThat(s.playerSchool().typeKey()).isEqualTo("DARK_HORSE");
        assertThat(s.playerSchool().strength()).isEqualTo(40);
        assertThat(s.playerSchool().region()).isEqualTo(1);
    }

    @Test
    void startingStatsFollowRanges() {
        for (long seed = 0; seed < 200; seed++) {
            GameState s = setup.create(seed);
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
        GameState s = setup.create(5);
        double fitness = s.stats.get("fitness");
        assertThat(s.stamina).isEqualTo(100 + (fitness - 50) / 2);
        assertThat(s.condition).isEqualTo(2);
        assertThat(s.academics).isEqualTo(50);
        assertThat(s.money).isZero();
        assertThat(s.reputation).isZero();
        assertThat(s.affinity).containsEntry(Axis.COACH, 30.0).containsEntry(Axis.TEAMMATE, 30.0)
                .containsEntry(Axis.FAMILY, 50.0).containsEntry(Axis.SCHOOL, 30.0);
        assertThat(s.menuTrainingCounts).hasSize(7).allSatisfy((k, v) -> assertThat(v).isZero());
    }

    @Test
    void sameSeedSameSetup() {
        GameState a = setup.create(99);
        GameState b = setup.create(99);
        assertThat(a.schools).isEqualTo(b.schools);
        assertThat(a.stats.asMap()).isEqualTo(b.stats.asMap());
        assertThat(a.cup.alive()).isEqualTo(b.cup.alive());
        assertThat(a.rng.state()).isEqualTo(b.rng.state());
    }
}
