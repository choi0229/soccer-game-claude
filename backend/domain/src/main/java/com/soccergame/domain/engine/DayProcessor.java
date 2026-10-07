package com.soccergame.domain.engine;

import com.soccergame.domain.calendar.GameCalendar;
import com.soccergame.domain.calendar.Weekday;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.config.Rules;
import com.soccergame.domain.config.TrainingMenus.TrainingMenu;
import com.soccergame.domain.event.EventEngine;
import com.soccergame.domain.event.EventSource;
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
    private final Modifiers modifiers;

    DayProcessor(GameConfig config, GameCalendar calendar, Resources resources, TrainingCalculator training,
                 Competitions competitions, EventEngine events, Modifiers modifiers) {
        this.config = config;
        this.rules = config.rules();
        this.calendar = calendar;
        this.resources = resources;
        this.training = training;
        this.competitions = competitions;
        this.events = events;
        this.modifiers = modifiers;
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
            trainingSlot(s, log, MORNING, Kind.TEAM, TrainingSlot.MORNING, newEvents);
        } else {
            classSlot(s, log, newEvents);
        }

        int cupRound = calendar.cupRound(s.week, s.day);
        boolean playerInCup = cupRound >= 0 && s.cup.isAlive(s.playerSchoolId);
        if (cupRound >= 0) {
            Competitions.RoundOutcome round = competitions.playCupRound(s, cupRound, date);
            if (round.paused()) {
                return pause(s, round.pending(), PendingMatch.DayKind.WEEKDAY, date, log, newEvents);
            }
            if (playerInCup) {
                return afterMatch(s, PendingMatch.DayKind.WEEKDAY, date, log, newEvents, round.record());
            }
        }
        if (s.makeupWeeks.containsKey(s.week)) {
            makeup(s, log);
        } else {
            trainingSlot(s, log, AFTERNOON, Kind.TEAM, TrainingSlot.AFTERNOON, newEvents);
        }
        trainingSlot(s, log, NIGHT, Kind.PERSONAL, TrainingSlot.NIGHT, newEvents);
        endOfDay(s, log);
        return new ActionOutcome(date, log, null, newEvents);
    }

    /** 경기가 승부처에서 멈췄다: 그날 나머지는 선택을 받은 뒤 afterMatch 로 처리한다 */
    private ActionOutcome pause(GameState s, PendingMatch pending, PendingMatch.DayKind kind, String date,
                                List<LogEntry> log, List<String> newEvents) {
        pending.dayKind = kind;
        pending.log = new ArrayList<>(log);
        pending.newEvents = new ArrayList<>(newEvents);
        s.pendingMatch = pending;
        List<LogEntry> shown = new ArrayList<>(log);
        shown.add(new LogEntry("경기", "승부처! 선택을 기다리는 중"));
        return new ActionOutcome(date, shown, null, newEvents, true);
    }

    /** 멈춘 경기의 승부처 선택을 받아 경기를 끝내고 그날 나머지를 처리한다 */
    ActionOutcome resumeMatch(GameState s, int choiceIndex) {
        PendingMatch pending = s.pendingMatch;
        MatchRecord record = competitions.resolve(s, pending, choiceIndex);
        s.pendingMatch = null;
        return afterMatch(s, pending.dayKind, pending.dateLabel, pending.log, pending.newEvents, record);
    }

    /** 플레이어 경기 뒤의 하루 마무리 (평일: 밤, 토요일: 컨디션 판정과 밤) */
    private ActionOutcome afterMatch(GameState s, PendingMatch.DayKind kind, String date, List<LogEntry> logSoFar,
                                     List<String> newEvents, MatchRecord match) {
        List<LogEntry> log = new ArrayList<>(logSoFar);
        String competition = match.competition() == com.soccergame.domain.model.Competition.CUP
                ? calendar.cupName() : "주말리그";
        log.add(new LogEntry(kind == PendingMatch.DayKind.WEEKDAY ? "오후·야간" : "경기",
                competition + " " + matchSummary(match)));
        if (kind == PendingMatch.DayKind.SATURDAY) {
            saturdayConditionCheck(s, log);
        }
        endOfDay(s, log);
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
        if (rule.scaleAcademicsByFriends()) {
            academics *= resources.friendMultiplier(s);
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
        if (rule.friendAffinity() != 0) {
            resources.affinity(s, Axis.FRIEND, rule.friendAffinity());
            parts.add(Axis.FRIEND.label() + " 관계도 " + Resources.signed(rule.friendAffinity()));
        }
        log.add(new LogEntry(MORNING, rule.name() + ": " + String.join(", ", parts)));
        if (rule.friendEventChance() > 0 && s.rng.chance(rule.friendEventChance())) {
            events.trigger(s, Axis.FRIEND, EventSource.CLASS).ifPresent(e -> newEvents.add(e.id()));
        }
    }

    /** 나머지 공부: 성장 없음. 훈련이 아니므로 체력 고갈·부상 판정을 하지 않고, 부상 중에도 한다. */
    private void makeup(GameState s, List<LogEntry> log) {
        Rules.Makeup m = rules.academics().makeup();
        resources.academics(s, m.academics());
        resources.stamina(s, m.stamina());
        s.metrics.makeupDays++;
        log.add(new LogEntry(AFTERNOON, "나머지 공부: 학업 성취 " + Resources.signed(m.academics()) + ", 체력 "
                + Resources.signed(m.stamina()) + " (지난주를 학업 성취 " + round2(s.makeupWeeks.get(s.week))
                + "로 마쳐 " + Resources.plain(m.below()) + " 미만)"));
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

    private void trainingSlot(GameState s, List<LogEntry> log, String slotName, Kind kind, TrainingSlot slot,
                              List<String> newEvents) {
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
        Modifiers.TrainingBonus bonus = modifiers.training(s, slot, menu.id());
        Map<String, Double> gains = training.train(s, menu, st.baseGrowth(), resources.conditionLevel(s).training(),
                bonus.applied());
        resources.stamina(s, st.stamina());
        s.menuTrainingCounts.merge(menu.id(), 1, Integer::sum);
        s.metrics.trainingSessions++;
        s.metrics.menuTrainings++;
        s.metrics.bonusApplied += bonus.applied();
        if (bonus.capped()) {
            s.metrics.bonusCapped++;
        }
        List<String> parts = new ArrayList<>();
        gains.forEach((k, v) -> parts.add(config.stat(k).name() + " +" + String.format("%.3f", v)));
        log.add(new LogEntry(slotName, kind.label + " [" + menu.name() + "] " + String.join(", ", parts)
                + bonusText(bonus) + ", 체력 " + Resources.signed(st.stamina())));
        injuryRoll(s, log, slotName, risky);
        EventSource source = slot == TrainingSlot.AFTERNOON ? EventSource.TEAM_TRAINING
                : slot == TrainingSlot.NIGHT ? EventSource.NIGHT_TRAINING : null;
        if (source != null && !s.isInjured() && s.rng.chance(rules.events().trainingChance())) {
            events.trigger(s, null, source).ifPresent(e -> newEvents.add(e.id()));
        }
    }

    /** 예: " (승부사 +5%, 감독 인연 +3%)" 또는 상한에 걸리면 " (… 합계 +36% → 상한 +30%)" */
    private static String bonusText(Modifiers.TrainingBonus bonus) {
        if (bonus.parts().isEmpty()) {
            return "";
        }
        List<String> items = new ArrayList<>();
        bonus.parts().forEach(p -> items.add(p.label() + " +" + Modifiers.pct(p.value())));
        String text = " (" + String.join(", ", items);
        if (bonus.capped()) {
            text += ", 합계 +" + Modifiers.pct(bonus.raw()) + " → 상한 +" + Modifiers.pct(bonus.applied());
        }
        return text + ")";
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
        List<String> newEvents = new ArrayList<>();
        String date = calendar.label(s.week, s.day);
        s.excludedToday = false;
        int cupRound = calendar.cupRound(s.week, s.day);
        Competitions.RoundOutcome round = null;
        if (cupRound >= 0) {
            round = competitions.playCupRound(s, cupRound, date);
        } else if (s.day == calendar.leagueMatchDay() && calendar.leagueRound(s.week) > 0) {
            round = competitions.playLeagueRound(s, calendar.leagueRound(s.week), date);
        }
        if (round != null && round.paused()) {
            return pause(s, round.pending(), PendingMatch.DayKind.SATURDAY, date, log, newEvents);
        }
        if (round != null && round.record() != null) {
            return afterMatch(s, PendingMatch.DayKind.SATURDAY, date, log, newEvents, round.record());
        }
        log.add(new LogEntry("토요일", "경기가 없는 토요일. 조용히 지나갔다."));
        saturdayConditionCheck(s, log);
        endOfDay(s, log);
        return new ActionOutcome(date, log, null, newEvents);
    }

    private void saturdayConditionCheck(GameState s, List<LogEntry> log) {
        if (s.stamina < rules.condition().saturdayDropBelowStamina()) {
            resources.condition(s, -1);
            log.add(new LogEntry("토요일", "체력이 떨어진 채 한 주를 마쳐 컨디션이 한 단계 내려갔다."));
        }
    }

    // ---------------- 일요일 ----------------

    ActionOutcome sunday(GameState s, SundayActivity activity, Axis meetTarget) {
        List<LogEntry> log = new ArrayList<>();
        List<String> newEvents = new ArrayList<>();
        String date = calendar.label(s.week, s.day);
        Rules.SundayRules sr = rules.daily().sunday();
        double staminaBefore = s.stamina;
        boolean metGirlfriend = activity == SundayActivity.MEET && meetTarget == Axis.GIRLFRIEND;
        String activityStamina = null;
        double activityDelta = 0;
        switch (activity) {
            case REST -> {
                resources.stamina(s, sr.rest().stamina());
                resources.condition(s, sr.rest().condition());
                activityStamina = "휴식";
                activityDelta = sr.rest().stamina();
                log.add(new LogEntry("일요일", "휴식: 체력 " + Resources.signed(sr.rest().stamina()) + ", 컨디션 "
                        + Resources.signed(sr.rest().condition())));
            }
            case PART_TIME -> {
                resources.money(s, sr.partTime().money());
                resources.stamina(s, sr.partTime().stamina());
                activityStamina = "아르바이트";
                activityDelta = sr.partTime().stamina();
                log.add(new LogEntry("일요일", "아르바이트: 돈 +" + sr.partTime().money() + "원, 체력 "
                        + Resources.signed(sr.partTime().stamina())));
            }
            case MEET -> {
                resources.affinity(s, meetTarget, sr.meet().affinity());
                log.add(new LogEntry("일요일", meetTarget.label() + "을(를) 만났다: " + meetTarget.label() + " 관계도 "
                        + Resources.signed(sr.meet().affinity())));
                events.trigger(s, meetTarget, EventSource.MEET).ifPresent(e -> newEvents.add(e.id()));
            }
        }
        // 무작위 이벤트는 밤 회복 전에 뽑는다
        if (s.rng.chance(rules.events().sundayChance())) {
            events.trigger(s, null, EventSource.SUNDAY).ifPresent(e -> newEvents.add(e.id()));
        }
        sundayNight(s, log, activityStamina, activityDelta, staminaBefore);
        endOfWeek(s, log, metGirlfriend);
        return new ActionOutcome(date, log, null, newEvents);
    }

    // ---------------- 공통 ----------------

    /** 방학 주와 학기 중의 밤 회복량이 다르다 */
    private double nightRecovery(GameState s) {
        return calendar.isVacation(s.week) ? rules.daily().vacationNightRecovery() : rules.daily().nightRecovery();
    }

    /** 일요일 밤: 회복 내역을 나눠 기록한다 (자유 행동, 밤 회복, 일요일 추가 회복, 합계) */
    private void sundayNight(GameState s, List<LogEntry> log, String activity, double activityDelta,
                             double staminaBefore) {
        double night = nightRecovery(s);
        double extra = rules.daily().sundayExtraRecovery();
        List<Modifiers.Part> bondParts = modifiers.nightRecoveryBonus(s);
        double bond = bondParts.stream().mapToDouble(Modifiers.Part::value).sum();
        s.metrics.staminaSum[s.week] += s.stamina;
        s.metrics.staminaSamples[s.week]++;
        resources.stamina(s, night + bond + extra);
        double nominal = activityDelta + night + bond + extra;
        double actual = s.stamina - staminaBefore;
        StringBuilder text = new StringBuilder("체력 ");
        if (activity != null) {
            text.append(activity).append(' ').append(Resources.signed(activityDelta)).append(", ");
        }
        text.append(calendar.isVacation(s.week) ? "방학 밤 회복 " : "밤 회복 ").append(Resources.signed(night));
        bondParts.forEach(p -> text.append(", ").append(p.label()).append(' ').append(Resources.signed(p.value())));
        text.append(", 일요일 추가 회복 ").append(Resources.signed(extra))
                .append(", 합계 ").append(Resources.signed(nominal));
        if (Math.abs(actual - nominal) > 1e-9) {
            text.append(" (최대 체력 ").append(Math.round(resources.maxStamina(s))).append(" 제한으로 실제 ")
                    .append(Resources.signed(round2(actual))).append(')');
        }
        text.append(" → 현재 ").append(Math.round(s.stamina));
        log.add(new LogEntry("밤", text.toString()));
    }

    /** 평일·토요일 밤: 밤 회복 + 인연(가족) 추가 회복 */
    private void endOfDay(GameState s, List<LogEntry> log) {
        double night = nightRecovery(s);
        List<Modifiers.Part> bondParts = modifiers.nightRecoveryBonus(s);
        double recovery = night + bondParts.stream().mapToDouble(Modifiers.Part::value).sum();
        s.metrics.staminaSum[s.week] += s.stamina;
        s.metrics.staminaSamples[s.week]++;
        resources.stamina(s, recovery);
        StringBuilder detail = new StringBuilder();
        bondParts.forEach(p -> detail.append(", ").append(p.label()).append(' ').append(Resources.signed(p.value())));
        log.add(new LogEntry("밤", "잠자리에 들었다: 체력 " + Resources.signed(recovery)
                + (detail.isEmpty() ? "" : " (밤 회복 " + Resources.signed(night) + detail + ")")
                + " (현재 " + Math.round(s.stamina) + ")"));
        if (s.day != Weekday.SUN) {
            s.day = Weekday.values()[s.day.ordinal() + 1];
        }
    }

    /** 일요일이 끝나면 학기 중 학업 감소, 나머지 공부 판정을 하고 다음 주 월요일로 넘어간다. */
    private void endOfWeek(GameState s, List<LogEntry> log, boolean metGirlfriend) {
        if (s.affinity.containsKey(Axis.GIRLFRIEND) && !metGirlfriend) {
            double change = rules.relationships().girlfriend().missedWeekChange();
            resources.affinity(s, Axis.GIRLFRIEND, change);
            log.add(new LogEntry("관계", "이번 주 " + Axis.GIRLFRIEND.label() + "를 만나지 않아 관계도 "
                    + Resources.signed(change) + " (현재 " + Math.round(s.affinity.get(Axis.GIRLFRIEND)) + ")"));
        }
        double rate = modifiers.semesterWeeklyRate(s);
        if (rate != 0 && !calendar.isVacation(s.week)) {
            double weekly = s.academics * rate;
            resources.academics(s, weekly);
            boolean changed = rate != rules.academics().semesterWeeklyRate();
            log.add(new LogEntry("학업", "학기 중 한 주가 지나 학업 성취 " + Resources.signed(round2(weekly))
                    + " (감소율 " + Modifiers.pct(-rate) + (changed ? ", 모범생" : "") + ", 현재 "
                    + Math.round(s.academics) + ")"));
        }
        Rules.Makeup makeup = rules.academics().makeup();
        if (s.academics < makeup.below() && s.week + 1 < calendar.weeksPerYear()) {
            s.makeupWeeks.put(s.week + 1, s.academics);
            log.add(new LogEntry("학업", "학업 성취 " + round2(s.academics) + "로 한 주를 마쳐 다음 주 오후 팀 훈련이 나머지 공부로 바뀐다."));
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
