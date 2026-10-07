package com.soccergame.domain.calendar;

import com.soccergame.domain.config.Rules.CalendarRules;
import com.soccergame.domain.config.Rules;
import com.soccergame.domain.config.Rules.CupDay;
import com.soccergame.domain.config.Rules.Period;
import com.soccergame.domain.config.Rules.TournamentDef;
import com.soccergame.domain.config.Rules.LeagueBlock;
import com.soccergame.domain.config.Rules.Vacation;
import com.soccergame.domain.config.Rules.WeekRef;
import com.soccergame.domain.config.ConfigException;

import java.util.List;
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
        // 경기일이 겹치면 안 된다 (리그 토요일, 토너먼트끼리)
        java.util.Set<Integer> used = new java.util.HashSet<>();
        for (int w = 0; w < rules.weeksPerYear(); w++) {
            if (leagueRound(w) > 0) {
                used.add(GameCalendar.absoluteDay(w, rules.league().matchDay()));
            }
        }
        java.util.Set<String> keys = new java.util.HashSet<>();
        for (TournamentDef t : rules.tournaments()) {
            if (!keys.add(t.key())) {
                throw new ConfigException("대회 key 중복: " + t.key());
            }
            if (t.matchDays().size() != t.rounds()) {
                throw new ConfigException("대회 " + t.key() + " 의 경기일 수가 라운드 수와 다릅니다");
            }
            if (t.entry().rule() == Rules.EntryRule.LEAGUE_TOP && t.entry().topPerRegion() == null) {
                throw new ConfigException("대회 " + t.key() + " 의 권역별 진출 수가 없습니다");
            }
            for (CupDay d : t.matchDays()) {
                int day = GameCalendar.absoluteDay(weekIndex(d.month(), d.week()), d.day());
                if (!used.add(day)) {
                    throw new ConfigException("대회 " + t.key() + " 의 경기일이 다른 경기와 겹칩니다: " + d);
                }
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

    /** 그날 열리는 토너먼트 라운드 */
    public record TournamentRound(TournamentDef tournament, int round) {
    }

    /** 그날의 토너먼트 라운드 (없으면 null) */
    public TournamentRound tournamentRound(int week, Weekday day) {
        for (TournamentDef t : rules.tournaments()) {
            for (int i = 0; i < t.matchDays().size(); i++) {
                CupDay d = t.matchDays().get(i);
                if (weekIndex(d.month(), d.week()) == week && d.day() == day) {
                    return new TournamentRound(t, i);
                }
            }
        }
        return null;
    }

    public List<TournamentDef> tournaments() {
        return rules.tournaments();
    }

    /** 토너먼트 첫 경기가 있는 주 (이 주가 시작할 때 대진을 추첨한다) */
    public int firstWeek(TournamentDef t) {
        CupDay d = t.matchDays().getFirst();
        return weekIndex(d.month(), d.week());
    }

    /** 리그 마지막 라운드가 있는 주 */
    public int leagueEndWeek() {
        return rules.league().blocks().stream().mapToInt(b -> weekIndex(b.to())).max().orElse(-1);
    }

    /** 리그 라운드가 있는 주 중 week 이후(포함) 가장 이른 주. 없으면 -1 */
    public int nextLeagueWeek(int week) {
        for (int w = week; w < weeksPerYear(); w++) {
            if (leagueRound(w) > 0) {
                return w;
            }
        }
        return -1;
    }

    public String periodLabel(Period p) {
        return p.from().month() + "월 " + p.from().week() + "주~" + p.to().month() + "월 " + p.to().week() + "주";
    }

    public String dayLabel(CupDay d) {
        return label(weekIndex(d.month(), d.week()), d.day());
    }

    public String label(int week, Weekday day) {
        return month(week) + "월 " + weekOfMonth(week) + "주 " + day.label() + "요일";
    }
}
