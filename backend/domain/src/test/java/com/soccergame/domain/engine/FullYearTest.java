package com.soccergame.domain.engine;

import com.soccergame.domain.model.Competition;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FullYearTest {

    @Test
    void yearRunsToTheEnd() {
        GameState s = EngineTestSupport.playYear(123);
        assertThat(s.finished).isTrue();
        assertThat(s.week).isEqualTo(48);
        assertThat(s.league.roundsPlayed()).isEqualTo(14);
        assertThat(s.cup.roundsPlayed()).isEqualTo(5);
        assertThat(s.cup.alive()).hasSize(1);
        assertThat(s.matches.stream().filter(m -> m.competition() == Competition.LEAGUE)).hasSize(14);
        assertThat(s.matches.stream().filter(m -> m.competition() == Competition.CUP).count()).isBetween(1L, 5L);
        assertThat(s.academicChecks).hasSize(2);
        assertThat(s.league.standings().stream().mapToInt(r -> r.played)).allMatch(p -> p == 14);
    }

    @Test
    void sameSeedAndChoicesGiveSameResult() {
        GameState a = EngineTestSupport.playYear(77);
        GameState b = EngineTestSupport.playYear(77);
        assertThat(a.stats.asMap()).isEqualTo(b.stats.asMap());
        assertThat(a.matches).isEqualTo(b.matches);
        assertThat(a.eventHistory).isEqualTo(b.eventHistory);
        assertThat(a.rng.state()).isEqualTo(b.rng.state());
        assertThat(a.academics).isEqualTo(b.academics);
        assertThat(a.actionCount).isEqualTo(b.actionCount);
    }

    @Test
    void differentSeedsDiffer() {
        assertThat(EngineTestSupport.playYear(1).stats.asMap()).isNotEqualTo(EngineTestSupport.playYear(2).stats.asMap());
    }
}
