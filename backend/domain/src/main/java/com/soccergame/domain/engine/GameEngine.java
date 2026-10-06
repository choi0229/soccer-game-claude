package com.soccergame.domain.engine;

import com.soccergame.domain.calendar.GameCalendar;
import com.soccergame.domain.calendar.Weekday;
import com.soccergame.domain.config.Events.Choice;
import com.soccergame.domain.config.Events.EventDef;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.config.TrainingMenus.TrainingMenu;
import com.soccergame.domain.event.EventEngine;
import com.soccergame.domain.match.MatchEngine;
import com.soccergame.domain.model.SundayActivity;
import com.soccergame.domain.model.TrainingSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 게임 규칙의 진입점. 상태 생성과 행동 적용만 노출한다.
 * 행동은 먼저 검증하고, 검증에 실패하면 상태를 바꾸지 않고 InvalidActionException 을 던진다.
 */
public final class GameEngine {
    private final GameConfig config;
    private final GameCalendar calendar;
    private final Resources resources;
    private final GameSetup setup;
    private final DayProcessor days;
    private final Modifiers modifiers;

    public GameEngine(GameConfig config) {
        this.config = config;
        this.calendar = new GameCalendar(config.rules().calendar());
        this.resources = new Resources(config);
        MatchEngine matchEngine = new MatchEngine(config);
        EventEngine events = new EventEngine(config, calendar);
        Competitions competitions = new Competitions(config, calendar, resources, matchEngine);
        this.setup = new GameSetup(config, calendar, resources);
        this.modifiers = new Modifiers(config);
        this.days = new DayProcessor(config, calendar, resources, new TrainingCalculator(config), competitions, events,
                modifiers);
    }

    public GameConfig config() {
        return config;
    }

    public GameCalendar calendar() {
        return calendar;
    }

    public Resources resources() {
        return resources;
    }

    public Modifiers modifiers() {
        return modifiers;
    }

    public GameState newGame(long seed) {
        return setup.create(seed);
    }

    public Phase phase(GameState s) {
        if (!s.pendingEvents.isEmpty()) {
            return Phase.EVENT;
        }
        if (s.finished) {
            return Phase.FINISHED;
        }
        return switch (s.day) {
            case SAT -> Phase.SATURDAY;
            case SUN -> Phase.SUNDAY;
            default -> Phase.WEEKDAY;
        };
    }

    /** 오늘 플레이어 학교의 경기가 있는지 (화면 표시용) */
    public boolean playerMatchToday(GameState s) {
        if (s.finished) {
            return false;
        }
        if (calendar.cupRound(s.week, s.day) >= 0) {
            return s.cup.isAlive(s.playerSchoolId);
        }
        return s.day == calendar.leagueMatchDay() && calendar.leagueRound(s.week) > 0;
    }

    public ActionOutcome apply(GameState s, Action action) {
        Phase phase = phase(s);
        Map<String, Integer> tiersBefore = modifiers.snapshot(s);
        ActionOutcome outcome = switch (action) {
            case Action.Day day -> {
                if (phase != Phase.WEEKDAY && phase != Phase.SATURDAY) {
                    throw new InvalidActionException("지금은 하루 진행을 할 수 없습니다 (현재: " + phase + ")");
                }
                validateMenus(day.menus());
                if (day.dawn() != null) {
                    s.selections.dawn = day.dawn();
                }
                if (day.classAttitude() != null) {
                    s.selections.classAttitude = day.classAttitude();
                }
                if (day.menus() != null) {
                    s.selections.menus.putAll(day.menus());
                }
                yield s.day == Weekday.SAT ? days.saturday(s) : days.weekday(s);
            }
            case Action.Sunday sunday -> {
                if (phase != Phase.SUNDAY) {
                    throw new InvalidActionException("지금은 일요일 행동을 할 수 없습니다 (현재: " + phase + ")");
                }
                if (sunday.activity() == null) {
                    throw new InvalidActionException("일요일 행동을 골라 주세요");
                }
                if (sunday.activity() == SundayActivity.MEET
                        && (!config.rules().daily().sunday().meet().targets().contains(sunday.meetTarget())
                        || !s.affinity.containsKey(sunday.meetTarget()))) {
                    throw new InvalidActionException("만날 수 없는 상대입니다: " + sunday.meetTarget());
                }
                yield days.sunday(s, sunday.activity(), sunday.meetTarget());
            }
            case Action.EventChoice choice -> {
                if (phase != Phase.EVENT) {
                    throw new InvalidActionException("선택을 기다리는 이벤트가 없습니다");
                }
                yield resolveEvent(s, choice);
            }
        };
        List<String> notes = modifiers.changes(tiersBefore, modifiers.snapshot(s));
        if (!notes.isEmpty()) {
            List<LogEntry> log = new ArrayList<>(outcome.log());
            notes.forEach(n -> log.add(new LogEntry("알림", n)));
            outcome = new ActionOutcome(outcome.dateLabel(), log, outcome.match(), outcome.newEvents());
        }
        s.actionCount++;
        s.lastOutcome = outcome;
        return outcome;
    }

    private void validateMenus(Map<TrainingSlot, String> menus) {
        if (menus == null) {
            return;
        }
        for (Map.Entry<TrainingSlot, String> e : menus.entrySet()) {
            if (e.getKey() == null) {
                throw new InvalidActionException("알 수 없는 칸입니다");
            }
            TrainingMenu menu = config.menu(e.getValue())
                    .orElseThrow(() -> new InvalidActionException("알 수 없는 훈련 메뉴: " + e.getValue()));
            if (!menu.unlocked()) {
                throw new InvalidActionException("아직 해금되지 않은 메뉴: " + menu.name());
            }
        }
    }

    private ActionOutcome resolveEvent(GameState s, Action.EventChoice choice) {
        PendingEvent pending = s.pendingEvents.peekFirst();
        if (!pending.eventId().equals(choice.eventId())) {
            throw new InvalidActionException("지금 선택할 이벤트는 " + pending.eventId() + " 입니다");
        }
        EventDef def = config.event(pending.eventId()).orElseThrow();
        if (choice.choiceIndex() < 0 || choice.choiceIndex() >= def.choices().size()) {
            throw new InvalidActionException("없는 선택지입니다: " + choice.choiceIndex());
        }
        Choice picked = def.choices().get(choice.choiceIndex());
        String applied = resources.apply(s, picked.effects());
        if (picked.trait() != null) {
            s.traitScores.merge(picked.trait(), 1, Integer::sum);
            applied += ", 특성 " + config.trait(picked.trait()).name() + " +1";
        }
        s.pendingEvents.removeFirst();
        return new ActionOutcome(def.title(), List.of(new LogEntry("이벤트", picked.text() + " → " + applied)), null,
                List.of());
    }
}
