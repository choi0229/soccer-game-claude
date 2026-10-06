package com.soccergame.domain.competition;

import com.soccergame.domain.config.Rules.Points;
import com.soccergame.domain.random.Rng;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LeagueTest {
    private static final Points POINTS = new Points(3, 1, 0);

    @Test
    void doubleRoundRobinHomeAndAway() {
        League league = League.create(List.of(1, 2, 3, 4, 5, 6, 7, 8), new Rng(11));
        assertThat(league.roundCount()).isEqualTo(14);
        Map<String, Integer> pairings = new HashMap<>();
        for (int r = 1; r <= 14; r++) {
            Set<Integer> playing = new HashSet<>();
            for (League.Fixture f : league.fixtures(r)) {
                assertThat(playing.add(f.homeId())).isTrue();
                assertThat(playing.add(f.awayId())).isTrue();
                pairings.merge(f.homeId() + ">" + f.awayId(), 1, Integer::sum);
            }
            assertThat(playing).hasSize(8);
        }
        // 서로 다른 두 팀은 홈에서 한 번, 원정에서 한 번 만난다
        assertThat(pairings).hasSize(56).allSatisfy((k, v) -> assertThat(v).isEqualTo(1));
    }

    @Test
    void standingsTieBreakers() {
        League league = League.create(List.of(1, 2, 3, 4), new Rng(1));
        // 1·2·3 모두 승점 3. 골득실: 1(+2), 2(+2), 3(+1). 다득점: 1(3) > 2(2).
        league.record(List.of(
                new FixtureResult(1, 4, 3, 1, null),
                new FixtureResult(2, 4, 2, 0, null),
                new FixtureResult(3, 4, 1, 0, null)), POINTS);
        assertThat(league.standings().stream().map(r -> r.teamId).toList()).containsExactly(1, 2, 3, 4);

        League tie = League.create(List.of(5, 6), new Rng(1));
        tie.record(List.of(new FixtureResult(6, 5, 1, 1, null)), POINTS);
        // 모든 지표가 같으면 학교 ID 오름차순
        assertThat(tie.standings().stream().map(r -> r.teamId).toList()).containsExactly(5, 6);
        assertThat(tie.rankOf(6)).isEqualTo(2);
    }
}
