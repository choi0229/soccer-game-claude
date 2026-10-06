package com.soccergame.domain.calendar;

import com.soccergame.domain.config.Rules.CalendarRules;
import com.soccergame.domain.config.Rules.CupDay;
import com.soccergame.domain.config.Rules.LeagueBlock;
import com.soccergame.domain.config.Rules.Vacation;
import com.soccergame.domain.config.Rules.WeekRef;
import com.soccergame.domain.config.ConfigException;

import java.util.Optional;

/**
 * 주 번호(0부터)와 달력을 오간다. 주 0 = 시작 월 1주.
 * 하루의 절대 번호는 주 × 7 + 요일(월=0).
 */
public final class GameCalendar {
    private final CalendarRules rules;

    public GameCalendar(CalendarRules rules) {
        this.rules = rules;
        validate();
    }

    private void validate() {
        for (LeagueBlock block : rules.league().blocks()) {
            int weeks = weekIndex(block.to()) - weekIndex(block.from()) + 1;
            if (weeks != block.toRound() - block.fromRound() + 1) {
                throw new ConfigException("리그 라운드 수와 기간이 맞지 않습니다: " + block);
            }
        }
    }

    public int weeksPerYear() {
        return rules.weeksPerYear();
    }

    public int weekIndex(WeekRef ref) {
        return weekIndex(ref.month(), ref.week());
    }

    public int weekIndex(int month, int weekOfMonth) {
        int monthOffset = Math.floorMod(month - rules.startMonth(), 12);
        return monthOffset * rules.weeksPerMonth() + (weekOfMonth - 1);
    }

    public int month(int week) {
        return Math.floorMod(rules.startMonth() - 1 + week / rules.weeksPerMonth(), 12) + 1;
    }

    public int weekOfMonth(int week) {
        return week % rules.weeksPerMonth() + 1;
    }

    public static int absoluteDay(int week, Weekday day) {
        return week * 7 + day.ordinal();
    }

    public Optional<String> vacation(int week) {
        for (Vacation v : rules.vacations()) {
            if (week >= weekIndex(v.from()) && week <= weekIndex(v.to())) {
                return Optional.of(v.name());
            }
        }
        return Optional.empty();
    }

    public boolean isVacation(int week) {
        return vacation(week).isPresent();
    }

    /** 그 주의 리그 라운드(1부터). 없으면 0. */
    public int leagueRound(int week) {
        for (LeagueBlock block : rules.league().blocks()) {
            int start = weekIndex(block.from());
            if (week >= start && week <= weekIndex(block.to())) {
                return block.fromRound() + (week - start);
            }
        }
        return 0;
    }

    public Weekday leagueMatchDay() {
        return rules.league().matchDay();
    }

    public int leagueRounds() {
        return rules.league().blocks().stream().mapToInt(LeagueBlock::toRound).max().orElse(0);
    }

    /** 그날의 토너먼트 라운드 순번(0부터). 없으면 -1. */
    public int cupRound(int week, Weekday day) {
        for (int i = 0; i < rules.cup().matchDays().size(); i++) {
            CupDay d = rules.cup().matchDays().get(i);
            if (weekIndex(d.month(), d.week()) == week && d.day() == day) {
                return i;
            }
        }
        return -1;
    }

    public int cupRounds() {
        return rules.cup().matchDays().size();
    }

    public String cupName() {
        return rules.cup().name();
    }

    public String label(int week, Weekday day) {
        return month(week) + "월 " + weekOfMonth(week) + "주 " + day.label() + "요일";
    }
}
