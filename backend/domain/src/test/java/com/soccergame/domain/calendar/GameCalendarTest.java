package com.soccergame.domain.calendar;

import com.soccergame.domain.TestConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameCalendarTest {
    private final GameCalendar cal = new GameCalendar(TestConfig.load().rules().calendar());

    @Test
    void weekIndexing() {
        assertThat(cal.weekIndex(3, 1)).isZero();
        assertThat(cal.month(0)).isEqualTo(3);
        assertThat(cal.month(39)).isEqualTo(12);
        assertThat(cal.weekOfMonth(39)).isEqualTo(4);
        assertThat(cal.month(40)).isEqualTo(1);
        assertThat(cal.month(47)).isEqualTo(2);
        assertThat(cal.weekIndex(2, 4)).isEqualTo(47);
    }

    @Test
    void vacations() {
        assertThat(cal.isVacation(cal.weekIndex(7, 3))).isFalse();
        assertThat(cal.isVacation(cal.weekIndex(7, 4))).isTrue();
        assertThat(cal.isVacation(cal.weekIndex(8, 4))).isTrue();
        assertThat(cal.isVacation(cal.weekIndex(9, 1))).isFalse();
        assertThat(cal.isVacation(cal.weekIndex(12, 4))).isFalse();
        assertThat(cal.isVacation(cal.weekIndex(1, 1))).isTrue();
        assertThat(cal.isVacation(cal.weekIndex(2, 4))).isTrue();
        long vacationWeeks = java.util.stream.IntStream.range(0, 48).filter(cal::isVacation).count();
        assertThat(vacationWeeks).isEqualTo(5 + 8);
    }

    @Test
    void leagueSchedule() {
        assertThat(cal.leagueRound(cal.weekIndex(3, 2))).isZero();
        assertThat(cal.leagueRound(cal.weekIndex(3, 3))).isEqualTo(1);
        assertThat(cal.leagueRound(cal.weekIndex(5, 1))).isEqualTo(7);
        assertThat(cal.leagueRound(cal.weekIndex(5, 2))).isZero();
        assertThat(cal.leagueRound(cal.weekIndex(5, 4))).isEqualTo(8);
        assertThat(cal.leagueRound(cal.weekIndex(7, 2))).isEqualTo(14);
        assertThat(cal.leagueRound(cal.weekIndex(7, 3))).isZero();
        assertThat(cal.leagueRounds()).isEqualTo(14);
    }

    @Test
    void tournamentSchedule() {
        assertThat(cal.tournaments()).extracting(t -> t.name())
                .containsExactly("춘계배", "초여름배", "하계배", "왕중왕전", "추계배");
        // 춘계배: 1주차 수·토, 2주차 화·목·토
        assertThat(cal.tournamentRound(0, Weekday.WED).round()).isEqualTo(0);
        assertThat(cal.tournamentRound(0, Weekday.SAT).round()).isEqualTo(1);
        assertThat(cal.tournamentRound(1, Weekday.TUE).round()).isEqualTo(2);
        assertThat(cal.tournamentRound(1, Weekday.THU).round()).isEqualTo(3);
        assertThat(cal.tournamentRound(1, Weekday.SAT).round()).isEqualTo(4);
        assertThat(cal.tournamentRound(0, Weekday.MON)).isNull();
        // 초여름배 5월 2~3주, 하계배 7월 3~4주, 추계배 10월 1~2주
        assertThat(cal.tournamentRound(cal.weekIndex(5, 2), Weekday.WED).tournament().key()).isEqualTo("earlySummer");
        assertThat(cal.tournamentRound(cal.weekIndex(7, 4), Weekday.SAT).tournament().key()).isEqualTo("summer");
        assertThat(cal.tournamentRound(cal.weekIndex(10, 2), Weekday.SAT).round()).isEqualTo(4);
        // 왕중왕전: 16강, 1주차 수·토, 2주차 수·토
        var champions = cal.tournamentRound(cal.weekIndex(8, 2), Weekday.WED);
        assertThat(champions.tournament().key()).isEqualTo("champions");
        assertThat(champions.round()).isEqualTo(2);
        assertThat(champions.tournament().teams()).isEqualTo(16);
        assertThat(cal.tournamentRound(cal.weekIndex(8, 2), Weekday.TUE)).isNull();
        // 리그는 7월 2주에 끝난다 (전반기 기준)
        assertThat(cal.leagueEndWeek()).isEqualTo(cal.weekIndex(7, 2));
    }
}
