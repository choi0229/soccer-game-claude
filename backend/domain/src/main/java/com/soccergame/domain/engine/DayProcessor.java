package com.soccergame.domain.engine;

import com.soccergame.domain.calendar.GameCalendar;
import com.soccergame.domain.calendar.Weekday;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.config.Rules;
import com.soccergame.domain.config.TrainingMenus.TrainingMenu;
import com.soccergame.domain.event.EventEngine;
import com.soccergame.domain.match.MatchRecord;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.ClassAttitude;
import com.soccergame.domain.model.DawnChoice;
import com.soccergame.domain.model.SundayActivity;
import com.soccergame.domain.model.TrainingSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 하루 처리. 평일은 새벽 → 오전 → 오후 → 야간 순서로 계산한다.
 * 게임 판정 난수를 쓰는 곳: 수업 이벤트(30%), 부상(3%), 부상 기간, 경기, 일요일 이벤트(70%).
 */
final class DayProcessor {
    private static final String DAWN = "새벽";
    private static final String MORNING = "오전";
    private static final String AFTERNOON = "오후";
    private static final String NIGHT = "야간";

    private final GameConfig config;
    private final Rules rules;
    private final GameCalendar calendar;
    private final Resources resources;
    private final TrainingCalculator training;
    private final Competitions competitions;
    private final EventEngine events;

    DayProcessor(GameConfig config, GameCalendar calendar, Resources resources, TrainingCalculator training,
                 Competitions competitions, EventEngine events) {
        this.config = config;
        this.rules = config.rules();
        this.calendar = calendar;
        this.resources = resources;
        this.training = training;
        this.competitions = competitions;
        this.events = events;
    }

    /** 훈련 칸의 종류 */
    private enum Kind {
        TEAM("팀 훈련"), PERSONAL("개인 훈련");

        final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    // ---------------- 월~금 ----------------

    ActionOutcome weekday(GameState s) {
        List<LogEntry> log = new ArrayList<>();
        List<String> newEvents = new ArrayList<>();
        String date = calendar.label(s.week, s.day);
        s.excludedToday = false;
        boolean vacation = calendar.isVacation(s.week);

        dawn(s, log);
        if (vacation) {
            trainingSlot(s, log, MORNING, Kind.TEAM, TrainingSlot.MORNING);
        } else {
            classSlot(s, log, newEvents);
        }

        MatchRecord match = null;
        int cupRound = calendar.cupRound(s.week, s.day);
        boolean playerInCup = cupRound >= 0 && s.cup.isAlive(s.playerSchoolId);
        if (cupRound >= 0) {
            match = competitions.playCupRound(s, cupRound, calendar.label(s.week, s.day));
        }
        if (playerInCup) {
            log.add(new LogEntry("오후·야간", calendar.cupName() + " " + matchSummary(match)));
        } else {
            if (s.remedialWeeks.contains(s.week)) {
                remedial(s, log);
            } else {
                trainingSlot(s, log, AFTERNOON, Kind.TEAM, TrainingSlot.AFTERNOON);
            }
            trainingSlot(s, log, NIGHT, Kind.PERSONAL, TrainingSlot.NIGHT);
        }
        endOfDay(s, log, rules.daily().nightRecovery());
        return new ActionOutcome(date, log, match, newEvents);
    }

    private void dawn(GameState s, List<LogEntry> log) {
        DawnChoice choice = s.selections.dawn;
        if (choice == DawnChoice.EXERCISE && s.isInjured()) {
            log.add(new LogEntry(DAWN, "부상 중이라 개인 운동 대신 더 잤다."));
            choice = DawnChoice.SLEEP;
        }
        if (choice == DawnChoice.SLEEP) {
            resources.stamina(s, rules.daily().dawn().sleep().stamina());
            log.add(new LogEntry(DAWN, "더 자기: 체력 " + Resources.signed(rules.daily().dawn().sleep().stamina())));
            return;
        }
        if (!canTrain(s, log, DAWN)) {
            return;
        }
        Rules.DawnExercise ex = rules.daily().dawn().exercise();
        boolean risky = s.stamina < rules.stamina().injuryRiskBelow();
        s.stats.add(GameConfig.FITNESS, ex.fitness());
        resources.stamina(s, ex.stamina());
        s.metrics.trainingSessions++;
        log.add(new LogEntry(DAWN, "개인 운동: 기초체력 " + Resources.signed(ex.fitness()) + ", 체력 "
                + Resources.signed(ex.stamina())));
        injuryRoll(s, log, DAWN, risky);
    }

    private void classSlot(GameState s, List<LogEntry> log, List<String> newEvents) {
        ClassAttitude attitude = s.selections.classAttitude;
        Rules.ClassAttitudeRule rule = rules.daily().classAttitudes().get(attitude);
        double academics = rule.academics();
        if (rule.scaleAcademicsBySchoolLife()) {
            academics *= resources.schoolLifeMultiplier(s);
        }
        List<String> parts = new ArrayList<>();
        if (academics != 0) {
            resources.academics(s, academics);
            parts.add("학업 성취 " + Resources.signed(round2(academics)));
        }
        if (rule.stamina() != 0) {
            resources.stamina(s, rule.stamina());
            parts.add("체력 " + Resources.signed(rule.stamina()));
        }
        if (rule.schoolAffinity() != 0) {
            resources.affinity(s, Axis.SCHOOL, rule.schoolAffinity());
            parts.add("학교생활 관계도 " + Resources.signed(rule.schoolAffinity()));
        }
        log.add(new LogEntry(MORNING, rule.name() + ": " + String.join(", ", parts)));
        if (rule.schoolEventChance() > 0 && s.rng.chance(rule.schoolEventChance())) {
            events.trigger(s, Axis.SCHOOL, "CLASS").ifPresent(e -> newEvents.add(e.id()));
        }
    }

    private void remedial(GameState s, List<LogEntry> log) {
        resources.academics(s, rules.academics().remedialGain());
        log.add(new LogEntry(AFTERNOON, "보충수업: 학업 성취 " + Resources.signed(rules.academics().remedialGain())));
    }

    /** 체력 고갈 검사. 훈련할 수 없으면 false. */
    private boolean canTrain(GameState s, List<LogEntry> log, String slot) {
        if (s.excludedToday) {
            log.add(new LogEntry(slot, "체력이 바닥나 오늘 훈련에서 제외되었다."));
            return false;
        }
        if (s.stamina < rules.stamina().exhaustionBelow()) {
            s.excludedToday = true;
            s.metrics.exclusions++;
            resources.affinity(s, Axis.COACH, rules.stamina().exhaustionCoachDelta());
            log.add(new LogEntry(slot, "체력이 " + Math.round(s.stamina) + "까지 떨어져 오늘 남은 훈련에서 제외되었다. 감독 관계도 "
                    + Resources.signed(rules.stamina().exhaustionCoachDelta())));
            return false;
        }
        return true;
    }

    private void trainingSlot(GameState s, List<LogEntry> log, String slotName, Kind kind, TrainingSlot slot) {
        if (s.isInjured()) {
            log.add(new LogEntry(slotName, "재활 (" + kind.label + " 대신)"));
            return;
        }
        if (!canTrain(s, log, slotName)) {
            return;
        }
        TrainingMenu menu = config.menu(s.selections.menus.get(slot)).orElseThrow();
        Rules.SlotTraining st = kind == Kind.TEAM ? rules.training().team() : rules.training().personal();
        boolean risky = s.stamina < rules.stamina().injuryRiskBelow();
        Map<String, Double> gains = training.train(s, menu, st.baseGrowth(), resources.conditionLevel(s).training());
        resources.stamina(s, st.stamina());
        s.menuTrainingCounts.merge(menu.id(), 1, Integer::sum);
        s.metrics.trainingSessions++;
        List<String> parts = new ArrayList<>();
        gains.forEach((k, v) -> parts.add(config.stat(k).name() + " +" + String.format("%.3f", v)));
        log.add(new LogEntry(slotName, kind.label + " [" + menu.name() + "] " + String.join(", ", parts)
                + ", 체력 " + Resources.signed(st.stamina())));
        injuryRoll(s, log, slotName, risky);
    }

    /** 체력 30 미만에서 훈련했으면 3% 확률로 부상. 기간은 1~4주 균등. */
    private void injuryRoll(GameState s, List<LogEntry> log, String slotName, boolean risky) {
        if (!risky) {
            return;
        }
        if (s.rng.chance(rules.stamina().injuryChancePerSlot())) {
            int weeks = s.rng.pick(rules.stamina().injuryWeeks());
            s.injuredUntilDay = s.absoluteDay() + weeks * 7;
            s.metrics.injuries++;
            s.metrics.injuryDaysMissed += weeks * 7;
            log.add(new LogEntry(slotName, "무리한 훈련으로 부상! " + weeks + "주 동안 재활해야 한다."));
        }
    }

    // ---------------- 토요일 ----------------

    ActionOutcome saturday(GameState s) {
        List<LogEntry> log = new ArrayList<>();
        String date = calendar.label(s.week, s.day);
        s.excludedToday = false;
        MatchRecord match = null;
        int cupRound = calendar.cupRound(s.week, s.day);
        if (cupRound >= 0) {
            boolean playerInCup = s.cup.isAlive(s.playerSchoolId);
            MatchRecord m = competitions.playCupRound(s, cupRound, date);
            if (playerInCup) {
                match = m;
                log.add(new LogEntry("경기", calendar.cupName() + " " + matchSummary(m)));
            }
        } else if (s.day == calendar.leagueMatchDay() && calendar.leagueRound(s.week) > 0) {
            match = competitions.playLeagueRound(s, calendar.leagueRound(s.week), date);
            log.add(new LogEntry("경기", "주말리그 " + matchSummary(match)));
        }
        if (match == null) {
            log.add(new LogEntry("토요일", "경기가 없는 토요일. 조용히 지나갔다."));
        }
        if (s.stamina < rules.condition().saturdayDropBelowStamina()) {
            resources.condition(s, -1);
            log.add(new LogEntry("토요일", "체력이 떨어진 채 한 주를 마쳐 컨디션이 한 단계 내려갔다."));
        }
        endOfDay(s, log, rules.daily().nightRecovery());
        return new ActionOutcome(date, log, match, List.of());
    }

    // ---------------- 일요일 ----------------

    ActionOutcome sunday(GameState s, SundayActivity activity, Axis meetTarget) {
        List<LogEntry> log = new ArrayList<>();
        List<String> newEvents = new ArrayList<>();
        String date = calendar.label(s.week, s.day);
        Rules.SundayRules sr = rules.daily().sunday();
        switch (activity) {
            case REST -> {
                resources.stamina(s, sr.rest().stamina());
                resources.condition(s, sr.rest().condition());
                log.add(new LogEntry("일요일", "휴식: 체력 " + Resources.signed(sr.rest().stamina()) + ", 컨디션 "
                        + Resources.signed(sr.rest().condition())));
            }
            case PART_TIME -> {
                resources.money(s, sr.partTime().money());
                resources.stamina(s, sr.partTime().stamina());
                log.add(new LogEntry("일요일", "아르바이트: 돈 +" + sr.partTime().money() + "원, 체력 "
                        + Resources.signed(sr.partTime().stamina())));
            }
            case MEET -> {
                resources.affinity(s, meetTarget, sr.meet().affinity());
                log.add(new LogEntry("일요일", meetTarget.label() + "을(를) 만났다: " + meetTarget.label() + " 관계도 "
                        + Resources.signed(sr.meet().affinity())));
                events.trigger(s, meetTarget, "MEET").ifPresent(e -> newEvents.add(e.id()));
            }
        }
        endOfDay(s, log, rules.daily().nightRecovery() + rules.daily().sundayExtraRecovery());
        if (s.rng.chance(rules.events().sundayChance())) {
            events.trigger(s, null, "SUNDAY").ifPresent(e -> newEvents.add(e.id()));
        }
        endOfWeek(s, log);
        return new ActionOutcome(date, log, null, newEvents);
    }

    // ---------------- 공통 ----------------

    private void endOfDay(GameState s, List<LogEntry> log, double recovery) {
        s.metrics.staminaSum[s.week] += s.stamina;
        s.metrics.staminaSamples[s.week]++;
        resources.stamina(s, recovery);
        log.add(new LogEntry("밤", "잠자리에 들었다: 체력 " + Resources.signed(recovery) + " (현재 " + Math.round(s.stamina) + ")"));
        if (s.day != Weekday.SUN) {
            s.day = Weekday.values()[s.day.ordinal() + 1];
        }
    }

    /** 일요일이 끝나면 학업 점검을 하고 다음 주 월요일로 넘어간다. */
    private void endOfWeek(GameState s, List<LogEntry> log) {
        for (Rules.AcademicCheck check : rules.academics().checks()) {
            if (calendar.weekIndex(check.at()) != s.week) {
                continue;
            }
            boolean failed = s.academics < check.below();
            int weeks = 0;
            if (failed) {
                for (Rules.WeekRef ref : check.remedialWeeks()) {
                    int w = calendar.weekIndex(ref);
                    if (w > s.week && w < calendar.weeksPerYear()) {
                        s.remedialWeeks.add(w);
                        weeks++;
                    }
                }
            }
            String label = check.at().month() + "월 " + check.at().week() + "주 학업 점검";
            s.academicChecks.add(new AcademicCheckResult(label, s.academics, failed, weeks));
            log.add(new LogEntry("학업 점검", label + ": 학업 성취 " + Math.round(s.academics)
                    + (failed ? (weeks > 0 ? " → 보충수업 " + weeks + "주" : " → 기준 미달 (보충수업은 다음 학년)")
                    : " → 통과")));
        }
        s.week++;
        s.day = Weekday.MON;
        if (s.week >= calendar.weeksPerYear()) {
            s.finished = true;
        }
    }

    private static String matchSummary(MatchRecord m) {
        if (m == null) {
            return "";
        }
        String pk = m.penaltyWin() == null ? "" : (m.penaltyWin() ? " (승부차기 승)" : " (승부차기 패)");
        String rating = m.rating() == null ? "" : ", 평점 " + String.format("%.1f", m.rating());
        return m.roundLabel() + " vs " + m.opponentName() + " " + m.ourScore() + ":" + m.theirScore() + pk
                + " - " + m.role().label() + rating;
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
