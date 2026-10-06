package com.soccergame.domain.event;

import com.soccergame.domain.calendar.GameCalendar;
import com.soccergame.domain.config.Events.EventDef;
import com.soccergame.domain.config.Events.Trigger;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.engine.GameState;
import com.soccergame.domain.engine.PendingEvent;
import com.soccergame.domain.match.MatchRecord;
import com.soccergame.domain.model.Axis;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** 이벤트 발생 조건 검사와 발생 처리. 선택 적용은 GameEngine 이 한다. */
public final class EventEngine {
    private final GameConfig config;
    private final GameCalendar calendar;

    public EventEngine(GameConfig config, GameCalendar calendar) {
        this.config = config;
        this.calendar = calendar;
    }

    /** 지금 발생할 수 있는 이벤트 (설정 파일 순서). axis 가 null 이면 모든 축. */
    public List<EventDef> eligible(GameState s, Axis axis) {
        List<EventDef> list = new ArrayList<>();
        int cooldown = config.rules().events().cooldownWeeks();
        for (EventDef e : config.events().events()) {
            if (axis != null && e.axis() != axis) {
                continue;
            }
            Integer last = s.eventLastWeek.get(e.id());
            if (last != null && (e.once() || s.week - last < cooldown)) {
                continue;
            }
            if (s.pendingEvents.stream().anyMatch(p -> p.eventId().equals(e.id()))) {
                continue;
            }
            if (matches(s, e.triggerOrEmpty())) {
                list.add(e);
            }
        }
        return list;
    }

    /** 조건을 만족한 이벤트 중 1편을 게임 판정 난수로 골라 대기열에 넣는다. 없으면 아무 일도 없다. */
    public Optional<EventDef> trigger(GameState s, Axis axis, String source) {
        List<EventDef> candidates = eligible(s, axis);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        EventDef chosen = s.rng.pick(candidates);
        s.eventLastWeek.put(chosen.id(), s.week);
        s.flags.addAll(chosen.flagsOrEmpty());
        s.pendingEvents.addLast(new PendingEvent(chosen.id(), source));
        s.eventHistory.add(chosen.id());
        return Optional.of(chosen);
    }

    public boolean matches(GameState s, Trigger t) {
        int weekNumber = s.week + 1;
        if (t.minWeek() != null && weekNumber < t.minWeek()) return false;
        if (t.maxWeek() != null && weekNumber > t.maxWeek()) return false;
        if (t.vacation() != null && calendar.isVacation(s.week) != t.vacation()) return false;
        if (t.injured() != null && s.isInjured() != t.injured()) return false;
        if (t.lastMatchRole() != null) {
            MatchRecord last = s.lastMatch();
            if (last == null || last.role() != t.lastMatchRole()) return false;
        }
        if (!within(s.affinity.get(Axis.COACH), t.coachAffinityMin(), t.coachAffinityMax())) return false;
        if (!within(s.affinity.get(Axis.TEAMMATE), t.teammateAffinityMin(), t.teammateAffinityMax())) return false;
        if (!within(s.affinity.get(Axis.FAMILY), t.familyAffinityMin(), t.familyAffinityMax())) return false;
        if (!within(s.affinity.get(Axis.SCHOOL), t.schoolAffinityMin(), t.schoolAffinityMax())) return false;
        if (!within(s.stamina, t.staminaMin(), t.staminaMax())) return false;
        if (t.conditionMin() != null && s.condition < t.conditionMin()) return false;
        if (t.conditionMax() != null && s.condition > t.conditionMax()) return false;
        if (!within(s.academics, t.academicsMin(), t.academicsMax())) return false;
        if (t.moneyMin() != null && s.money < t.moneyMin()) return false;
        if (t.reputationMin() != null && s.reputation < t.reputationMin()) return false;
        if (t.requiresFlags() != null && !s.flags.containsAll(t.requiresFlags())) return false;
        if (t.excludesFlags() != null && t.excludesFlags().stream().anyMatch(s.flags::contains)) return false;
        return true;
    }

    private static boolean within(double value, Double min, Double max) {
        return (min == null || value >= min) && (max == null || value <= max);
    }
}
