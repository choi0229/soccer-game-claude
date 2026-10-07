package com.soccergame.domain.engine;

import com.soccergame.domain.calendar.Weekday;
import com.soccergame.domain.competition.FixtureResult;
import com.soccergame.domain.match.MatchRecord;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.Competition;
import com.soccergame.domain.model.MatchRole;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.soccergame.domain.engine.EngineTestSupport.ENGINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CompetitionsTest {

    @Test
    void cupWeekdayMatchReplacesAfternoonAndNight() {
        GameState s = ENGINE.newGame(1);
        EngineTestSupport.day(s);
        EngineTestSupport.day(s);
        assertThat(s.day).isEqualTo(Weekday.WED);
        ActionOutcome out = EngineTestSupport.day(s);
        assertThat(out.match()).isNotNull();
        assertThat(out.match().competition()).isEqualTo(Competition.CUP);
        assertThat(out.match().roundLabel()).isEqualTo("32강");
        assertThat(out.log()).extracting(LogEntry::slot).containsExactly("새벽", "오전", "오후·야간", "밤");
        assertThat(s.cups.get("spring").roundsPlayed()).isEqualTo(1);
        assertThat(s.cups.get("spring").alive()).hasSize(16);
    }

    @Test
    void afterEliminationCupDaysAreNormalDays() {
        for (long seed = 0; seed < 200; seed++) {
            GameState s = ENGINE.newGame(seed);
            for (int i = 0; i < 3; i++) {
                EngineTestSupport.day(s);
            }
            EngineTestSupport.resolveEvents(s);
            if (s.cups.get("spring").isAlive(s.playerSchoolId)) {
                continue;
            }
            EngineTestSupport.day(s); // 목
            EngineTestSupport.resolveEvents(s);
            EngineTestSupport.day(s); // 금
            EngineTestSupport.resolveEvents(s);
            ActionOutcome sat = EngineTestSupport.day(s);
            assertThat(sat.match()).isNull();
            assertThat(sat.log().getFirst().text()).contains("경기가 없는 토요일");
            // 다른 학교 대진은 계속 진행된다
            assertThat(s.cups.get("spring").roundsPlayed()).isEqualTo(2);
            return;
        }
        throw new AssertionError("1라운드 탈락 시드를 찾지 못했습니다");
    }

    @Test
    void everyTournamentBracketCompletes() {
        GameState s = EngineTestSupport.playYear(3);
        assertThat(s.cups).containsOnlyKeys("spring", "earlySummer", "summer", "champions", "autumn");
        for (var e : s.cups.entrySet()) {
            List<Integer> sizes = e.getValue().results().stream().map(List::size).toList();
            if (e.getKey().equals("champions")) {
                assertThat(sizes).containsExactly(8, 4, 2, 1);
            } else {
                assertThat(sizes).containsExactly(16, 8, 4, 2, 1);
            }
            for (List<FixtureResult> round : e.getValue().results()) {
                assertThat(round).allMatch(r -> r.winnerId() > 0);
            }
        }
        // 네 권역 리그를 모두 끝까지 계산한다
        assertThat(s.leagues.values()).allMatch(l -> l.roundsPlayed() == 14);
    }

    @Test
    void championsEntrantsAreTopFourOfEachRegion() {
        GameState s = EngineTestSupport.playYear(4);
        var entrants = s.cups.get("champions").entrants();
        assertThat(entrants).hasSize(16).doesNotHaveDuplicates();
        for (var league : s.leagues.values()) {
            var top4 = league.standings().stream().limit(4).map(r -> r.teamId).toList();
            assertThat(entrants).containsAll(top4);
        }
    }

    @Test
    void notQualifiedForChampionsMeansNormalDays() {
        for (long seed = 0; seed < 100; seed++) {
            GameState s = EngineTestSupport.playYear(seed);
            if (s.cups.get("champions").entrants().contains(s.playerSchoolId)) {
                continue;
            }
            assertThat(s.matches).noneMatch(m -> "champions".equals(m.tournamentKey()));
            assertThat(TournamentResult.of(ENGINE.calendar(), s, ENGINE.calendar().tournaments().get(3)).stage())
                    .isEqualTo(TournamentResult.Stage.NOT_ENTERED);
            return;
        }
        throw new AssertionError("왕중왕전에 못 나간 시드를 찾지 못했습니다");
    }

    @Test
    void reputationIsPaidPerTournament() {
        for (long seed = 0; seed < 300; seed++) {
            GameState s = EngineTestSupport.playYear(seed);
            long reached = s.cupStagesReached.values().stream().filter(l -> l.contains("8강")).count();
            if (reached >= 2) {
                assertThat(s.cupStagesReached.keySet().size()).isGreaterThanOrEqualTo(2);
                return;
            }
        }
        throw new AssertionError("두 대회에서 8강에 오른 시드를 찾지 못했습니다");
    }

    @Test
    void leagueMatchAppliesRoleCostsAndFeedback() {
        GameState s = ENGINE.newGame(4);
        s.week = ENGINE.calendar().weekIndex(4, 1);
        s.day = Weekday.SAT;
        s.affinity.put(Axis.COACH, 100.0);
        s.stamina = 50;
        double coach = s.affinity.get(Axis.COACH);
        double passiveSum = ENGINE.config().passiveStatKeys().stream().mapToDouble(s.stats::get).sum();
        ActionOutcome out = EngineTestSupport.day(s);
        MatchRecord m = out.match();
        assertThat(m.role()).isEqualTo(MatchRole.STARTER);
        assertThat(m.rating()).isNotNull();
        assertThat(s.stamina).isCloseTo(Math.min(50 - 12 + 8 + EngineTestSupport.FAMILY_BOND, ENGINE.resources().maxStamina(s)), within(1e-9));
        // 출전하면 패시브 4종 중 하나가 +0.5
        double after = ENGINE.config().passiveStatKeys().stream().mapToDouble(s.stats::get).sum();
        assertThat(after - passiveSum).isCloseTo(0.5, within(1e-9));
        assertThat(m.passiveGrowth()).isIn("기세", "클러치", "멘탈", "파이터형 대응", "커맨더형 대응");
        // 평판 = (평점 구간 + 골×2 + 도움×1) × 0.5
        double tier = m.rating() >= 8 ? 6 : m.rating() >= 7 ? 3 : 0;
        assertThat(m.reputationGained()).isCloseTo((tier + m.playerGoals() * 2 + m.playerAssists()) * 0.5, within(1e-9));
        double expectedCoach = m.rating() >= 7 ? 100 : m.rating() < 5.5 ? 98 : 100;
        assertThat(s.affinity.get(Axis.COACH)).isEqualTo(Math.min(expectedCoach, coach));
    }

    @Test
    void benchAndInjuredPlayersDoNotPlay() {
        GameState s = ENGINE.newGame(5);
        s.week = ENGINE.calendar().weekIndex(4, 1);
        s.day = Weekday.SAT;
        s.affinity.put(Axis.COACH, 0.0);
        ENGINE.config().trainedStatKeys().forEach(k -> s.stats.set(k, 10));
        double stamina = s.stamina = 50;
        MatchRecord bench = EngineTestSupport.day(s).match();
        assertThat(bench.role()).isEqualTo(MatchRole.BENCH);
        assertThat(bench.rating()).isNull();
        assertThat(bench.scenes()).isEmpty();
        assertThat(s.stamina).isEqualTo(stamina + 8 + EngineTestSupport.FAMILY_BOND);

        GameState t = ENGINE.newGame(5);
        t.week = ENGINE.calendar().weekIndex(4, 1);
        t.day = Weekday.SAT;
        t.injuredUntilDay = t.absoluteDay() + 7;
        assertThat(EngineTestSupport.day(t).match().role()).isEqualTo(MatchRole.ABSENT);
    }

    @Test
    void leagueTablePointsMatchResults() {
        GameState s = EngineTestSupport.playYear(6);
        int totalPoints = s.league.standings().stream().mapToInt(r -> r.points).sum();
        int expected = 0;
        for (int round = 1; round <= 14; round++) {
            for (FixtureResult r : s.league.results(round)) {
                expected += r.homeGoals() == r.awayGoals() ? 2 : 3;
            }
        }
        assertThat(totalPoints).isEqualTo(expected);
        int playerRank = s.league.rankOf(s.playerSchoolId);
        assertThat(playerRank).isBetween(1, 8);
    }
}
