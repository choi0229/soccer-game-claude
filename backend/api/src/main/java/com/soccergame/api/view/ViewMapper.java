package com.soccergame.api.view;

import com.soccergame.api.view.GameView.*;
import com.soccergame.domain.calendar.GameCalendar;
import com.soccergame.domain.calendar.Weekday;
import com.soccergame.domain.competition.League;
import com.soccergame.domain.config.Events;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.config.Rules;
import com.soccergame.domain.config.Schools;
import com.soccergame.domain.engine.GameEngine;
import com.soccergame.domain.engine.GameState;
import com.soccergame.domain.engine.PendingEvent;
import com.soccergame.domain.engine.Phase;
import com.soccergame.domain.engine.SeasonSummary;
import com.soccergame.domain.engine.StateFingerprint;
import com.soccergame.domain.match.MatchEngine;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.ClassAttitude;
import com.soccergame.domain.model.DawnChoice;
import com.soccergame.domain.model.School;
import com.soccergame.domain.model.SundayActivity;
import com.soccergame.domain.model.TrainingSlot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class ViewMapper {
    private final GameEngine engine;
    private final GameConfig config;
    private final GameCalendar calendar;
    private final MatchEngine matchEngine;

    public ViewMapper(GameEngine engine) {
        this.engine = engine;
        this.config = engine.config();
        this.calendar = engine.calendar();
        this.matchEngine = new MatchEngine(config);
    }

    public GameView toView(UUID runId, GameState s) {
        Phase phase = engine.phase(s);
        return new GameView(runId, s.seed, s.actionCount, StateFingerprint.of(s), phase, date(s), school(s),
                resources(s), stats(s), selections(s), slots(s), matchToday(s), options(), league(s), cup(s), pendingEvent(s),
                s.lastOutcome, List.copyOf(s.matches), SeasonSummary.of(config, s));
    }

    private DateView date(GameState s) {
        int week = Math.min(s.week, calendar.weeksPerYear() - 1);
        Weekday day = s.finished ? Weekday.SUN : s.day;
        String term = calendar.vacation(week).orElse("학기 중");
        return new DateView(week + 1, calendar.month(week), calendar.weekOfMonth(week), day.name(), day.label(),
                s.finished ? "시즌 종료" : calendar.label(week, day), term, calendar.isVacation(week));
    }

    private SchoolView school(GameState s) {
        School school = s.playerSchool();
        String region = config.schools().regions().stream().filter(r -> r.id() == school.region()).findFirst()
                .map(Schools.RegionDef::name).orElse("");
        return new SchoolView(school.id(), school.name(), school.typeName(), school.strength(), region);
    }

    private ResourcesView resources(GameState s) {
        Map<String, Double> affinity = new LinkedHashMap<>();
        s.affinity.forEach((axis, v) -> affinity.put(axis.name().toLowerCase(), v));
        String injuredUntil = null;
        if (s.isInjured()) {
            int week = s.injuredUntilDay / 7;
            Weekday day = Weekday.values()[s.injuredUntilDay % 7];
            injuredUntil = week >= calendar.weeksPerYear() ? "시즌 종료 후" : calendar.label(week, day);
        }
        double score = matchEngine.selectionScore(s.affinity.get(Axis.COACH), s.stats);
        return new ResourcesView(s.stamina, engine.resources().maxStamina(s), s.condition,
                engine.resources().conditionLevel(s).name(), s.money, s.academics, s.reputation, affinity,
                engine.resources().schoolLifeMultiplier(s), s.isInjured(), injuredUntil, score,
                matchEngine.role(score, s.playerSchool().strength(), s.isInjured()).label());
    }

    private List<StatView> stats(GameState s) {
        List<StatView> list = new ArrayList<>();
        for (String key : config.allStatKeys()) {
            boolean passive = config.passiveStatKeys().contains(key);
            double v = s.stats.get(key);
            list.add(new StatView(key, config.stat(key).name(), passive ? "passive" : config.stat(key).group(),
                    config.isPrimary(key), passive, v, s.initialStats.get(key), config.grade(v)));
        }
        return list;
    }

    private SelectionsView selections(GameState s) {
        Map<String, String> menus = new LinkedHashMap<>();
        s.selections.menus.forEach((slot, id) -> menus.put(slot.name(), id));
        return new SelectionsView(s.selections.dawn.name(), s.selections.classAttitude.name(), menus);
    }

    /** 오늘 하루의 칸 구성 (월~금만). 계산은 하지 않고 무엇을 하는 칸인지만 알려 준다. */
    private List<SlotView> slots(GameState s) {
        if (s.finished || !s.day.isWeekday()) {
            return List.of();
        }
        boolean vacation = calendar.isVacation(s.week);
        boolean injured = s.isInjured();
        boolean match = engine.playerMatchToday(s);
        boolean remedial = s.remedialWeeks.contains(s.week);
        List<SlotView> slots = new ArrayList<>();
        slots.add(new SlotView("DAWN", "새벽", "DAWN", "새벽 선택", null,
                injured ? "부상 중에는 개인 운동을 할 수 없어 더 자기로 처리됩니다" : null));
        if (vacation) {
            slots.add(injured ? fixed("MORNING", "오전", "재활", "부상 중")
                    : new SlotView("MORNING", "오전", "TRAINING", "팀 훈련", TrainingSlot.MORNING.name(), "방학"));
        } else {
            slots.add(new SlotView("MORNING", "오전", "CLASS", "수업", null, null));
        }
        if (match) {
            String label = calendar.cupName() + " 경기";
            slots.add(fixed("AFTERNOON", "오후", label, "오후와 야간 훈련 대신 경기를 치릅니다"));
            slots.add(fixed("NIGHT", "야간", label, null));
            return slots;
        }
        if (remedial) {
            slots.add(fixed("AFTERNOON", "오후", "보충수업", "학업 성취 미달로 팀 훈련 대신 보충수업"));
        } else if (injured) {
            slots.add(fixed("AFTERNOON", "오후", "재활", "부상 중"));
        } else {
            slots.add(new SlotView("AFTERNOON", "오후", "TRAINING", "팀 훈련", TrainingSlot.AFTERNOON.name(), null));
        }
        slots.add(injured ? fixed("NIGHT", "야간", "재활", "부상 중")
                : new SlotView("NIGHT", "야간", "TRAINING", "개인 훈련", TrainingSlot.NIGHT.name(), null));
        return slots;
    }

    /** 오늘 플레이어 학교 경기의 상대 (없으면 null) */
    private MatchPreview matchToday(GameState s) {
        if (!engine.playerMatchToday(s)) {
            return null;
        }
        String competition;
        String round;
        List<League.Fixture> fixtures;
        if (calendar.cupRound(s.week, s.day) >= 0) {
            fixtures = s.cup.currentPairs();
            competition = calendar.cupName();
            round = com.soccergame.domain.competition.Cup.roundName(fixtures.size() * 2);
        } else {
            int r = calendar.leagueRound(s.week);
            fixtures = s.league.fixtures(r);
            competition = "주말리그";
            round = r + "라운드";
        }
        for (League.Fixture f : fixtures) {
            if (f.homeId() == s.playerSchoolId || f.awayId() == s.playerSchoolId) {
                boolean home = f.homeId() == s.playerSchoolId;
                School o = s.school(home ? f.awayId() : f.homeId());
                return new MatchPreview(competition, round, o.id(), o.name(), o.typeName(), o.strength(),
                        config.defenderType(o.defenderType()).name(), home);
            }
        }
        return null;
    }

    private static SlotView fixed(String key, String label, String activity, String note) {
        return new SlotView(key, label, "FIXED", activity, null, note);
    }

    private OptionsView options() {
        Rules.DailyRules daily = config.rules().daily();
        List<Option> dawn = List.of(
                new Option(DawnChoice.EXERCISE.name(), DawnChoice.EXERCISE.label(),
                        "기초체력 " + signed(daily.dawn().exercise().fitness()) + ", 체력 " + signed(daily.dawn().exercise().stamina())),
                new Option(DawnChoice.SLEEP.name(), DawnChoice.SLEEP.label(), "체력 " + signed(daily.dawn().sleep().stamina())));
        List<Option> classes = new ArrayList<>();
        for (ClassAttitude a : ClassAttitude.values()) {
            Rules.ClassAttitudeRule r = daily.classAttitudes().get(a);
            List<String> parts = new ArrayList<>();
            if (r.academics() != 0) parts.add("학업 " + signed(r.academics()) + (r.scaleAcademicsBySchoolLife() ? "×학교생활 배율" : ""));
            if (r.stamina() != 0) parts.add("체력 " + signed(r.stamina()));
            if (r.schoolAffinity() != 0) parts.add("학교생활 관계도 " + signed(r.schoolAffinity()));
            if (r.schoolEventChance() > 0) parts.add(Math.round(r.schoolEventChance() * 100) + "% 학교생활 이벤트");
            classes.add(new Option(a.name(), r.name(), String.join(", ", parts)));
        }
        List<MenuOption> menus = config.trainingMenus().menus().stream()
                .filter(m -> m.unlocked())
                .map(m -> new MenuOption(m.id(), m.name(), m.stats().stream().map(k -> config.stat(k).name()).toList(),
                        m.growthMultiplier()))
                .toList();
        Rules.SundayRules sr = daily.sunday();
        List<Option> sunday = List.of(
                new Option(SundayActivity.REST.name(), SundayActivity.REST.label(),
                        "체력 " + signed(sr.rest().stamina()) + ", 컨디션 1단계 상승"),
                new Option(SundayActivity.PART_TIME.name(), SundayActivity.PART_TIME.label(),
                        "돈 +" + sr.partTime().money() + "원, 체력 " + signed(sr.partTime().stamina())),
                new Option(SundayActivity.MEET.name(), SundayActivity.MEET.label(),
                        "관계도 " + signed(sr.meet().affinity()) + ", 그 축 이벤트 1편"));
        List<Option> targets = sr.meet().targets().stream()
                .map(a -> new Option(a.name().toLowerCase(), a.label(), null)).toList();
        return new OptionsView(dawn, classes, menus, sunday, targets);
    }

    private List<StandingView> league(GameState s) {
        List<StandingView> rows = new ArrayList<>();
        int rank = 1;
        for (League.Row r : s.league.standings()) {
            School school = s.school(r.teamId);
            rows.add(new StandingView(rank++, r.teamId, school.name(), school.typeName(), school.strength(), r.played,
                    r.won, r.drawn, r.lost, r.goalsFor, r.goalsAgainst, r.goalDiff(), r.points,
                    r.teamId == s.playerSchoolId));
        }
        return rows;
    }

    private CupView cup(GameState s) {
        return new CupView(calendar.cupName(), s.cup.isAlive(s.playerSchoolId), s.cup.roundsPlayed(),
                List.copyOf(s.cupStagesReached));
    }

    private EventView pendingEvent(GameState s) {
        PendingEvent pending = s.pendingEvents.peekFirst();
        if (pending == null) {
            return null;
        }
        Events.EventDef def = config.event(pending.eventId()).orElseThrow();
        return new EventView(def.id(), def.axis().label(), def.title(), def.body(), pending.source(),
                def.choices().stream().map(Events.Choice::text).toList());
    }

    private static String signed(double v) {
        return com.soccergame.domain.engine.Resources.signed(v);
    }
}
