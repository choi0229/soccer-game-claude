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
    void cupSchedule() {
        assertThat(cal.cupRound(0, Weekday.WED)).isEqualTo(0);
        assertThat(cal.cupRound(0, Weekday.SAT)).isEqualTo(1);
        assertThat(cal.cupRound(1, Weekday.TUE)).isEqualTo(2);
        assertThat(cal.cupRound(1, Weekday.THU)).isEqualTo(3);
        assertThat(cal.cupRound(1, Weekday.SAT)).isEqualTo(4);
        assertThat(cal.cupRound(0, Weekday.MON)).isEqualTo(-1);
        assertThat(cal.cupRounds()).isEqualTo(5);
    }
}
