package com.soccergame.simulator;

import com.soccergame.domain.calendar.GameCalendar;
import com.soccergame.domain.config.Events.EventDef;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.engine.Action;
import com.soccergame.domain.engine.ActionOutcome;
import com.soccergame.domain.engine.GameEngine;
import com.soccergame.domain.engine.GameState;
import com.soccergame.domain.engine.LogEntry;
import com.soccergame.domain.engine.Modifiers;
import com.soccergame.domain.engine.PendingEvent;
import com.soccergame.domain.engine.TournamentResult;
import com.soccergame.domain.match.ClutchLog;
import com.soccergame.domain.match.MatchRecord;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.MatchRole;
import com.soccergame.domain.model.Stats;
import com.soccergame.domain.random.RandomStreams;
import com.soccergame.domain.random.Rng;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 한 판을 YearRunner 와 같은 순서로 진행하면서 주별로 기록해 시즌 기록 문서(마크다운)를 만든다.
 * 게임 판정에는 관여하지 않고 행동 전후 상태만 비교한다.
 */
public final class SeasonLog {
    private final GameEngine engine;
    private final GameConfig config;
    private final GameCalendar calendar;
    private final Modifiers modifiers;

    public SeasonLog(GameConfig config) {
        this.config = config;
        this.engine = new GameEngine(config);
        this.calendar = engine.calendar();
        this.modifiers = engine.modifiers();
    }

    record EventRow(String id, String title, String choice, String day, int seenBefore) {
    }

    static final class Week {
        final int index;
        final List<MatchRecord> matches = new ArrayList<>();
        final List<EventRow> events = new ArrayList<>();
        final List<String> notes = new ArrayList<>();
        final List<String> gradeUps = new ArrayList<>();
        final List<Double> dayStamina = new ArrayList<>();
        double endStamina;
        double academics;
        double primaryAvg;
        double reputation;

        Week(int index) {
            this.index = index;
        }

        boolean quiet() {
            return matches.isEmpty() && events.isEmpty() && notes.isEmpty() && gradeUps.isEmpty();
        }
    }

    /** 판 하나의 기록 */
    public static final class Run {
        public final String schoolType;
        public final String strategy;
        public final long seed;
        GameState state;
        Stats startStats;
        double startAcademics;
        double startReputation;
        Map<Axis, Double> startAffinity;
        final List<Week> weeks = new ArrayList<>();

        Run(String schoolType, String strategy, long seed) {
            this.schoolType = schoolType;
            this.strategy = strategy;
            this.seed = seed;
        }
    }

    public Run play(long seed, Strategy strategy) {
        GameState s = engine.newGame(seed);
        Run run = new Run(s.playerSchool().typeName(), strategy.name(), seed);
        run.state = s;
        run.startStats = s.stats.copy();
        run.startAcademics = s.academics;
        run.startReputation = s.reputation;
        run.startAffinity = new EnumMap<>(s.affinity);
        Rng choice = RandomStreams.choiceStream(seed);
        Map<String, Integer> seen = new LinkedHashMap<>();
        Week week = new Week(0);
        /** 생긴 순서대로: 이벤트가 생긴 주와 요일 (일요일 이벤트는 다음 주 월요일 전에 고른다) */
        java.util.ArrayDeque<Object[]> triggered = new java.util.ArrayDeque<>();
        while (true) {
            if (engine.phase(s) == com.soccergame.domain.engine.Phase.FINISHED) {
                finishWeek(run, week, s);
                return run;
            }
            int w = Math.min(s.week, calendar.weeksPerYear() - 1);
            String day = s.day.label();
            int matchesBefore = s.matches.size();
            int samplesBefore = s.metrics.staminaSamples[w];
            double sumBefore = s.metrics.staminaSum[w];
            int injuries = s.metrics.injuries;
            int exclusions = s.metrics.exclusions;
            int makeup = s.metrics.makeupDays;
            int condition = s.condition;
            Map<String, Integer> tiers = modifiers.snapshot(s);
            Map<String, String> grades = grades(s.stats);

            ActionOutcome outcome;
            /** 이 행동으로 생긴 일을 적을 주와 요일 (이벤트 선택은 이벤트가 생긴 때) */
            Week noteWeek = week;
            String noteDay = day;
            switch (engine.phase(s)) {
                case MATCH -> {
                    var options = engine.clutchOptions(s);
                    outcome = engine.apply(s, new Action.ClutchChoice(s.pendingMatch.session.moment().id(),
                            strategy.clutchChoice(s, options, choice)));
                }
                case EVENT -> {
                    PendingEvent pending = s.pendingEvents.peekFirst();
                    EventDef def = config.event(pending.eventId()).orElseThrow();
                    int index = strategy.eventChoice(s, def, choice);
                    outcome = engine.apply(s, new Action.EventChoice(def.id(), index));
                    int before = seen.getOrDefault(def.id(), 0);
                    seen.put(def.id(), before + 1);
                    Object[] origin = triggered.pollFirst();
                    if (origin == null || !def.id().equals(origin[2])) {
                        throw new IllegalStateException("이벤트 발생 기록이 어긋남: " + def.id());
                    }
                    Week target = origin == null ? week : (Week) origin[0];
                    String when = (origin == null ? day : (String) origin[1]) + ", "
                            + com.soccergame.domain.event.EventSource.valueOf(pending.source()).label();
                    target.events.add(new EventRow(def.id(), def.title(), def.choices().get(index).text(), when, before));
                    noteWeek = target;
                    noteDay = (String) origin[1];
                }
                case WEEKDAY, SATURDAY -> outcome = engine.apply(s, strategy.day(s, engine, choice));
                case SUNDAY -> outcome = engine.apply(s, strategy.sunday(s, engine, choice));
                default -> throw new IllegalStateException("끝난 판");
            }

            for (String id : outcome.newEvents()) {
                triggered.addLast(new Object[] {week, day, id});
            }
            week.matches.addAll(s.matches.subList(matchesBefore, s.matches.size()));
            if (s.metrics.staminaSamples[w] > samplesBefore) {
                week.dayStamina.add(s.metrics.staminaSum[w] - sumBefore);
            }
            if (s.metrics.injuries > injuries) {
                String text = outcome.log().stream().map(LogEntry::text).filter(t -> t.contains("부상!"))
                        .findFirst().orElse("부상");
                noteWeek.notes.add("부상(" + noteDay + "): " + text.replace("무리한 훈련으로 부상! ", ""));
            }
            if (s.metrics.exclusions > exclusions) {
                noteWeek.notes.add("훈련 제외(" + noteDay + ")");
            }
            if (s.metrics.makeupDays > makeup) {
                noteWeek.notes.add("나머지 공부(" + noteDay + ")");
            }
            if (s.condition != condition) {
                noteWeek.notes.add("컨디션 " + conditionName(condition) + "→" + conditionName(s.condition) + "(" + noteDay + ")");
            }
            for (String change : modifiers.changes(tiers, modifiers.snapshot(s))) {
                noteWeek.notes.add(change.replaceFirst(": .*", "") + "(" + noteDay + ")");
            }
            Map<String, String> after = grades(s.stats);
            for (var g : grades.entrySet()) {
                String now = after.get(g.getKey());
                if (!g.getValue().equals(now)) {
                    String text = config.stat(g.getKey()).name() + " " + g.getValue() + "→" + now;
                    if (gradeRank(now) < gradeRank(g.getValue())) {
                        noteWeek.gradeUps.add(text);
                    } else {
                        noteWeek.notes.add("등급 하락: " + text + "(" + noteDay + ")");
                    }
                }
            }

            if (s.week != w || s.finished) {
                finishWeek(run, week, s);
                if (!s.finished) {
                    week = new Week(s.week);
                }
            }
        }
    }

    /** 주를 마친 값을 적는다. 마지막 주는 판이 끝난 뒤 남은 이벤트를 고르고 다시 적는다 */
    private void finishWeek(Run run, Week week, GameState s) {
        if (!run.weeks.contains(week)) {
            run.weeks.add(week);
        }
        week.endStamina = s.stamina;
        week.academics = s.academics;
        week.primaryAvg = s.stats.average(primary());
        week.reputation = s.reputation;
    }

    private Map<String, String> grades(Stats stats) {
        Map<String, String> map = new LinkedHashMap<>();
        config.allStatKeys().forEach(k -> map.put(k, config.grade(stats.get(k))));
        return map;
    }

    /** 등급 순서 (0 이 가장 높음) */
    private int gradeRank(String grade) {
        var grades = config.rules().stats().grades();
        for (int i = 0; i < grades.size(); i++) {
            if (grades.get(i).grade().equals(grade)) {
                return i;
            }
        }
        return grades.size();
    }

    private String conditionName(int level) {
        return config.rules().condition().levels().get(level).name();
    }

    private List<String> primary() {
        return config.rules().archetype().primaryStats();
    }

    private List<String> otherTrained() {
        return config.trainedStatKeys().stream().filter(k -> !config.isPrimary(k)).toList();
    }

    // ---------------- 문서 ----------------

    public String render(Run run) {
        StringBuilder md = new StringBuilder();
        GameState s = run.state;
        md.append("# 시즌 기록: ").append(run.schoolType).append(" + ").append(run.strategy).append("\n\n");
        md.append("- 시드 ").append(run.seed).append(", 학교: ").append(s.playerSchool().name())
                .append(" (").append(run.schoolType).append(", 전력 ").append(fmt0(s.playerSchool().strength()))
                .append(", ").append(config.schools().regions().get(s.playerSchool().region() - 1).name())
                .append(" 권역)\n");
        md.append("- 시뮬레이터 `--season-log`로 만든 문서입니다. 전략의 선택 규칙은 시뮬레이터와 같습니다.\n");
        md.append("- 체력: 하루를 마친 뒤 밤 회복 전 값으로 월별 평균·최저를 냈습니다. 주별 표의 체력은 일요일 밤 회복 뒤(다음 주 시작) 값이고, 괄호는 그 주 하루 끝 최저입니다.\n");
        md.append("- 이벤트는 생긴 주와 요일, 생긴 곳(수업 시간·팀 훈련·야간 훈련·만남·일요일)으로 적었습니다. 일요일에 생긴 이벤트는 실제로는 다음 주 월요일 일과 전에 고릅니다.\n");
        md.append("- 주력 3종: ").append(String.join(", ", primary().stream().map(k -> config.stat(k).name()).toList()))
                .append("\n\n");
        overview(md, run);
        monthly(md, run);
        weekly(md, run);
        flow(md, run);
        return md.toString();
    }

    private void overview(StringBuilder md, Run run) {
        GameState s = run.state;
        md.append("## 1. 한눈에 보기\n\n### 최종 성적\n\n");
        md.append("| 항목 | 값 |\n|---|---|\n");
        md.append("| 리그 순위 | ").append(s.league.rankOf(s.playerSchoolId)).append("위 / ")
                .append(s.league.standings().size()).append("개교 (")
                .append(leagueRecord(s)).append(") |\n");
        for (TournamentResult t : TournamentResult.of(calendar, s)) {
            md.append("| ").append(t.name()).append(" | ").append(t.text());
            if (t.matches() > 0) {
                md.append(" (").append(t.matches()).append("경기)");
            }
            md.append(" |\n");
        }
        Map<MatchRole, Integer> roles = roleCounts(s.matches);
        md.append("| 경기 수 | ").append(s.matches.size()).append(" (선발 ").append(roles.get(MatchRole.STARTER))
                .append(" / 교체 ").append(roles.get(MatchRole.SUB)).append(" / 벤치 ").append(roles.get(MatchRole.BENCH))
                .append(" / 부상 결장 ").append(roles.get(MatchRole.ABSENT)).append(") |\n");
        md.append("| 골 / 도움 | ").append(sum(s.matches, true)).append(" / ").append(sum(s.matches, false)).append(" |\n");
        md.append("| 평균 평점 | ").append(avgRating(s.matches)).append(" |\n");
        md.append("| 평판 | ").append(fmt1(run.startReputation)).append(" → ").append(fmt1(s.reputation)).append(" |\n\n");

        md.append("### 시작과 끝\n\n| 항목 | 시작 | 끝 | 변화 |\n|---|---|---|---|\n");
        for (String k : primary()) {
            row(md, config.stat(k).name() + " (" + config.grade(run.startStats.get(k)) + "→"
                    + config.grade(s.stats.get(k)) + ")", run.startStats.get(k), s.stats.get(k));
        }
        row(md, "주력 3종 평균", run.startStats.average(primary()), s.stats.average(primary()));
        row(md, "나머지 훈련 능력치 " + otherTrained().size() + "종 평균", run.startStats.average(otherTrained()),
                s.stats.average(otherTrained()));
        row(md, "보조 능력치 " + config.passiveStatKeys().size() + "종 평균", run.startStats.average(config.passiveStatKeys()),
                s.stats.average(config.passiveStatKeys()));
        row(md, "학업 성취", run.startAcademics, s.academics);
        for (Axis axis : Axis.values()) {
            if (!axis.hasAffinity()) {
                continue;
            }
            Double start = run.startAffinity.get(axis);
            Double end = s.affinity.get(axis);
            String label = "관계도: " + axis.label();
            if (end == null) {
                md.append("| ").append(label).append(" | 잠김 | 잠김 | - |\n");
            } else if (start == null) {
                md.append("| ").append(label).append(" | 잠김 | ").append(fmt1(end)).append(" | ")
                        .append(s.axisUnlockedWeek.get(axis) + 1).append("주차에 열림 |\n");
            } else {
                row(md, label, start, end);
            }
        }
        md.append("\n| 특성 | 점수 | 단계 |\n|---|---|---|\n");
        for (var trait : config.rules().traits().list()) {
            md.append("| ").append(trait.name()).append(" | ").append(s.traitScores.getOrDefault(trait.key(), 0))
                    .append(" | ").append(modifiers.traitTier(s, trait.key())).append(" |\n");
        }
        md.append("\n| 인연 | 관계도 | 단계 |\n|---|---|---|\n");
        for (var bond : config.rules().bonds().axes()) {
            int tier = modifiers.bondTier(s, bond.axis());
            Double a = s.affinity.get(bond.axis());
            md.append("| ").append(bond.axis().label()).append(" | ").append(a == null ? "잠김" : fmt1(a))
                    .append(" | ").append(tier < 0 ? "잠김" : String.valueOf(tier)).append(" |\n");
        }
        md.append('\n');
    }

    private void row(StringBuilder md, String label, double start, double end) {
        md.append("| ").append(label).append(" | ").append(fmt1(start)).append(" | ").append(fmt1(end))
                .append(" | ").append(signed(end - start)).append(" |\n");
    }

    private void monthly(StringBuilder md, Run run) {
        md.append("## 2. 월별 표\n\n");
        md.append("| 월 | 학기/방학 주 | 경기 (승-무-패) | 역할 (선발/교체/벤치/결장) | 골·도움 | 평균 평점 | 주력 평균 (상승) | 체력 평균 / 최저 | 학업 | 이벤트 (처음/다시) | 승부처 | 평판 |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        double prevPrimary = run.startStats.average(primary());
        Map<Integer, List<Week>> byMonth = new LinkedHashMap<>();
        for (Week w : run.weeks) {
            byMonth.computeIfAbsent(calendar.month(w.index), k -> new ArrayList<>()).add(w);
        }
        for (var entry : byMonth.entrySet()) {
            List<Week> weeks = entry.getValue();
            List<MatchRecord> matches = weeks.stream().flatMap(w -> w.matches.stream()).toList();
            long vacation = weeks.stream().filter(w -> calendar.isVacation(w.index)).count();
            Map<MatchRole, Integer> roles = roleCounts(matches);
            List<Double> stamina = weeks.stream().flatMap(w -> w.dayStamina.stream()).toList();
            List<EventRow> events = weeks.stream().flatMap(w -> w.events.stream()).toList();
            long first = events.stream().filter(e -> e.seenBefore() == 0).count();
            long clutches = matches.stream().filter(m -> m.clutch() != null).count();
            Week last = weeks.getLast();
            md.append("| ").append(entry.getKey()).append("월 | ").append(weeks.size() - vacation).append(" / ")
                    .append(vacation).append(" | ").append(matches.size()).append(" (").append(wdl(matches)).append(") | ")
                    .append(roles.get(MatchRole.STARTER)).append("/").append(roles.get(MatchRole.SUB)).append("/")
                    .append(roles.get(MatchRole.BENCH)).append("/").append(roles.get(MatchRole.ABSENT)).append(" | ")
                    .append(sum(matches, true)).append("·").append(sum(matches, false)).append(" | ")
                    .append(avgRating(matches)).append(" | ").append(fmt1(last.primaryAvg)).append(" (")
                    .append(signed(last.primaryAvg - prevPrimary)).append(") | ")
                    .append(fmt1(stamina.stream().mapToDouble(d -> d).average().orElse(0))).append(" / ")
                    .append(fmt1(stamina.stream().mapToDouble(d -> d).min().orElse(0))).append(" | ")
                    .append(fmt1(last.academics)).append(" | ").append(events.size()).append(" (").append(first)
                    .append("/").append(events.size() - first).append(") | ").append(clutches).append(" | ")
                    .append(fmt1(last.reputation)).append(" |\n");
            prevPrimary = last.primaryAvg;
        }
        md.append("\n승부처는 출전한 경기에서 나온 수, 학업·주력 평균·평판은 그 달 마지막 주를 마친 값입니다.\n\n");
    }

    private void weekly(StringBuilder md, Run run) {
        md.append("## 3. 주별 기록\n\n");
        md.append("| 주차 | 날짜 | 구분 | 경기 | 이벤트 | 눈에 띄는 일 | 체력 | 학업 | 주력 |\n");
        md.append("|---|---|---|---|---|---|---|---|---|\n");
        for (Week w : run.weeks) {
            List<String> matches = w.matches.stream().map(this::matchText).toList();
            List<String> events = w.events.stream().map(e -> "「" + e.title() + "」(" + e.day() + ") → '" + e.choice()
                    + "' · " + (e.seenBefore() == 0 ? "처음" : "다시(" + (e.seenBefore() + 1) + "번째)")).toList();
            List<String> notes = new ArrayList<>(w.notes);
            if (!w.gradeUps.isEmpty()) {
                notes.add("등급 상승: " + String.join(", ", w.gradeUps));
            }
            md.append("| ").append(w.index + 1).append(" | ").append(calendar.month(w.index)).append("월 ")
                    .append(calendar.weekOfMonth(w.index)).append("주 | ")
                    .append(calendar.vacation(w.index).orElse("학기")).append(" | ").append(cell(matches)).append(" | ")
                    .append(cell(events)).append(" | ").append(cell(notes)).append(" | ").append(fmt0(w.endStamina))
                    .append(" (").append(fmt0(w.dayStamina.stream().mapToDouble(d -> d).min().orElse(w.endStamina)))
                    .append(")")
                    .append(" | ").append(fmt1(w.academics)).append(" | ").append(fmt1(w.primaryAvg)).append(" |\n");
        }
        md.append('\n');
    }

    private String matchText(MatchRecord m) {
        StringBuilder t = new StringBuilder();
        t.append(m.competitionName());
        if (m.roundLabel() != null) {
            t.append(' ').append(m.roundLabel());
        }
        t.append(" vs ").append(m.opponentName()).append(' ').append(m.ourScore()).append('-').append(m.theirScore())
                .append(' ').append(resultLabel(m));
        if (m.penaltyWin() != null) {
            t.append(m.penaltyWin() ? " (승부차기 승)" : " (승부차기 패)");
        }
        t.append(" · ").append(m.role().label());
        if (m.rating() != null) {
            t.append(" · 평점 ").append(fmt1(m.rating()));
        }
        if (m.playerGoals() > 0) {
            t.append(" · ").append(m.playerGoals()).append("골");
        }
        if (m.playerAssists() > 0) {
            t.append(" · ").append(m.playerAssists()).append("도움");
        }
        ClutchLog c = m.clutch();
        if (c != null) {
            t.append(" · 승부처 「").append(c.title()).append("」 ").append(c.minute()).append("분 '").append(c.choiceText())
                    .append("' → ").append(c.success() ? "성공" : "실패");
        }
        return t.toString();
    }

    private void flow(StringBuilder md, Run run) {
        md.append("## 4. 흐름 분석\n\n");
        int quiet = 0;
        int best = 0;
        int bestEnd = -1;
        int streak = 0;
        for (Week w : run.weeks) {
            if (w.quiet()) {
                quiet++;
                streak++;
                if (streak > best) {
                    best = streak;
                    bestEnd = w.index;
                }
            } else {
                streak = 0;
            }
        }
        md.append("- 경기·이벤트·눈에 띄는 일이 모두 없던 주: ").append(quiet).append("주");
        if (best > 0) {
            md.append(", 가장 길게 이어진 구간: ").append(best).append("주 (").append(weekLabel(bestEnd - best + 1))
                    .append(" ~ ").append(weekLabel(bestEnd)).append(")");
        }
        md.append('\n');

        Week firstRepeat = run.weeks.stream().filter(w -> w.events.stream().anyMatch(e -> e.seenBefore() > 0))
                .findFirst().orElse(null);
        md.append("- 이벤트를 처음 다시 본 주: ");
        if (firstRepeat == null) {
            md.append("없음\n");
        } else {
            EventRow e = firstRepeat.events.stream().filter(x -> x.seenBefore() > 0).findFirst().orElseThrow();
            md.append(firstRepeat.index + 1).append("주차 (").append(weekLabel(firstRepeat.index)).append(", 「")
                    .append(e.title()).append("」)\n");
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, String> titles = new LinkedHashMap<>();
        run.weeks.forEach(w -> w.events.forEach(e -> {
            counts.merge(e.id(), 1, Integer::sum);
            titles.put(e.id(), e.title());
        }));
        int total = counts.values().stream().mapToInt(i -> i).sum();
        md.append("- 이벤트: 모두 ").append(total).append("번, 서로 다른 이벤트 ").append(counts.size())
                .append("종. 가장 많이 나온 3개(같은 횟수면 먼저 나온 순): ");
        md.append(String.join(", ", counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue()).limit(3)
                .map(e -> "「" + titles.get(e.getKey()) + "」 " + e.getValue() + "번").toList())).append('\n');

        md.append("- 능력치 등급이 오른 시점:\n");
        boolean any = false;
        for (Week w : run.weeks) {
            if (!w.gradeUps.isEmpty()) {
                any = true;
                md.append("  - ").append(w.index + 1).append("주차 (").append(weekLabel(w.index)).append("): ")
                        .append(String.join(", ", w.gradeUps)).append('\n');
            }
        }
        if (!any) {
            md.append("  - 없음\n");
        }
        MatchRecord firstStart = run.state.matches.stream().filter(m -> m.role() == MatchRole.STARTER).findFirst()
                .orElse(null);
        MatchRecord firstGoal = run.state.matches.stream().filter(m -> m.playerGoals() > 0).findFirst().orElse(null);
        md.append("- 처음 선발로 나온 주: ").append(firstText(firstStart)).append('\n');
        md.append("- 처음 골을 넣은 주: ").append(firstText(firstGoal)).append('\n');
    }

    private String firstText(MatchRecord m) {
        return m == null ? "없음" : (m.week() + 1) + "주차 (" + m.dateLabel() + ", " + m.competitionName() + ")";
    }

    // ---------------- 요약 (README 비교용) ----------------

    /** 세 판 비교 표의 한 열에 들어갈 값들 */
    public Map<String, String> summary(Run run) {
        GameState s = run.state;
        Map<String, String> m = new LinkedHashMap<>();
        Map<MatchRole, Integer> roles = roleCounts(s.matches);
        m.put("학교", s.playerSchool().name());
        m.put("리그 순위", s.league.rankOf(s.playerSchoolId) + "위 (" + leagueRecord(s) + ")");
        for (TournamentResult t : TournamentResult.of(calendar, s)) {
            m.put(t.name(), t.text());
        }
        m.put("경기 (선발/교체/벤치/결장)", s.matches.size() + " (" + roles.get(MatchRole.STARTER) + "/"
                + roles.get(MatchRole.SUB) + "/" + roles.get(MatchRole.BENCH) + "/" + roles.get(MatchRole.ABSENT) + ")");
        m.put("골 / 도움", sum(s.matches, true) + " / " + sum(s.matches, false));
        m.put("평균 평점", avgRating(s.matches));
        m.put("평판", fmt1(s.reputation));
        m.put("주력 3종 평균", fmt1(run.startStats.average(primary())) + " → " + fmt1(s.stats.average(primary())));
        m.put("나머지 훈련 능력치 평균", fmt1(run.startStats.average(otherTrained())) + " → "
                + fmt1(s.stats.average(otherTrained())));
        m.put("학업 성취", fmt1(run.startAcademics) + " → " + fmt1(s.academics));
        m.put("나머지 공부 주", String.valueOf(s.makeupWeeks.size()));
        m.put("부상 / 훈련 제외", s.metrics.injuries + " / " + s.metrics.exclusions);
        long events = run.weeks.stream().mapToLong(w -> w.events.size()).sum();
        long repeats = run.weeks.stream().flatMap(w -> w.events.stream()).filter(e -> e.seenBefore() > 0).count();
        m.put("이벤트 (다시 본 것)", events + " (" + repeats + ")");
        long quiet = run.weeks.stream().filter(Week::quiet).count();
        m.put("조용한 주", String.valueOf(quiet));
        MatchRecord firstStart = s.matches.stream().filter(x -> x.role() == MatchRole.STARTER).findFirst().orElse(null);
        MatchRecord firstGoal = s.matches.stream().filter(x -> x.playerGoals() > 0).findFirst().orElse(null);
        m.put("첫 선발 주차", firstStart == null ? "없음" : String.valueOf(firstStart.week() + 1));
        m.put("첫 골 주차", firstGoal == null ? "없음" : String.valueOf(firstGoal.week() + 1));
        return m;
    }

    // ---------------- 도움 함수 ----------------

    private String weekLabel(int week) {
        return calendar.month(week) + "월 " + calendar.weekOfMonth(week) + "주";
    }

    private static String leagueRecord(GameState s) {
        var row = s.league.standings().stream().filter(r -> r.teamId == s.playerSchoolId).findFirst().orElseThrow();
        return row.won + "승 " + row.drawn + "무 " + row.lost + "패";
    }

    private static Map<MatchRole, Integer> roleCounts(List<MatchRecord> matches) {
        Map<MatchRole, Integer> map = new EnumMap<>(MatchRole.class);
        for (MatchRole r : MatchRole.values()) {
            map.put(r, 0);
        }
        matches.forEach(m -> map.merge(m.role(), 1, Integer::sum));
        return map;
    }

    private static int sum(List<MatchRecord> matches, boolean goals) {
        return matches.stream().mapToInt(m -> goals ? m.playerGoals() : m.playerAssists()).sum();
    }

    private static String avgRating(List<MatchRecord> matches) {
        var ratings = matches.stream().filter(m -> m.rating() != null).mapToDouble(MatchRecord::rating).toArray();
        if (ratings.length == 0) {
            return "-";
        }
        double sum = 0;
        for (double r : ratings) {
            sum += r;
        }
        return String.format(Locale.ROOT, "%.2f", sum / ratings.length);
    }

    private static String wdl(List<MatchRecord> matches) {
        int w = 0;
        int d = 0;
        int l = 0;
        for (MatchRecord m : matches) {
            switch (m.result()) {
                case "W" -> w++;
                case "D" -> d++;
                default -> l++;
            }
        }
        return w + "-" + d + "-" + l;
    }

    private static String resultLabel(MatchRecord m) {
        return switch (m.result()) {
            case "W" -> "승";
            case "D" -> "무";
            default -> "패";
        };
    }

    private static String cell(List<String> items) {
        return items.isEmpty() ? "" : String.join("<br>", items).replace("|", "\\|");
    }

    static String fmt0(double v) {
        return String.format(Locale.ROOT, "%.0f", v);
    }

    static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    static String signed(double v) {
        return (v >= 0 ? "+" : "") + fmt1(v);
    }
}
