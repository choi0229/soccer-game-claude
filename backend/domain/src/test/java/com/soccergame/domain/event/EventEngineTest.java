package com.soccergame.domain.event;

import com.soccergame.domain.TestConfig;
import com.soccergame.domain.calendar.GameCalendar;
import com.soccergame.domain.calendar.Weekday;
import com.soccergame.domain.config.Events;
import com.soccergame.domain.config.Events.EventDef;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.engine.Action;
import com.soccergame.domain.event.EventSource;
import com.soccergame.domain.engine.ActionOutcome;
import com.soccergame.domain.engine.GameEngine;
import com.soccergame.domain.engine.GameState;
import com.soccergame.domain.engine.InvalidActionException;
import com.soccergame.domain.engine.Phase;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.ClassAttitude;
import com.soccergame.domain.model.DawnChoice;
import com.soccergame.domain.model.SundayActivity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventEngineTest {
    private final GameConfig config = TestConfig.load();
    private final GameEngine engine = new GameEngine(config);
    private final EventEngine events = new EventEngine(config, new GameCalendar(config.rules().calendar()));

    private GameState semester(long seed) {
        GameState s = engine.newGame(seed);
        s.week = engine.calendar().weekIndex(11, 1);
        s.day = Weekday.MON;
        return s;
    }

    private EventDef def(String id) {
        return config.event(id).orElseThrow();
    }

    private boolean eligible(GameState s, String id) {
        return events.eligible(s, null).stream().anyMatch(e -> e.id().equals(id));
    }

    @Test
    void repeatableEventHasTwelveWeekCooldown() {
        GameState s = semester(1);
        assertThat(eligible(s, "family_001")).isTrue();
        s.eventLastWeek.put("family_001", s.week);
        assertThat(eligible(s, "family_001")).isFalse();
        s.week += 11;
        assertThat(eligible(s, "family_001")).isFalse();
        s.week += 1;
        assertThat(eligible(s, "family_001")).isTrue();
    }

    @Test
    void onceEventNeverReturns() {
        GameState s = semester(1);
        s.week = 0;
        assertThat(eligible(s, "coach_001")).isTrue();
        s.eventLastWeek.put("coach_001", 0);
        s.week = 3;
        assertThat(eligible(s, "coach_001")).isFalse();
    }

    @Test
    void triggerConditions() {
        GameState s = semester(1);
        // 주차 범위
        s.week = 4;
        assertThat(eligible(s, "coach_001")).isFalse();
        // 직전 경기 역할: 경기 기록이 없으면 불가
        assertThat(eligible(s, "coach_005")).isFalse();
        // 방학
        assertThat(eligible(s, "coach_008")).isFalse();
        s.week = engine.calendar().weekIndex(8, 1);
        assertThat(eligible(s, "coach_008")).isTrue();
        assertThat(eligible(s, "friend_001")).isFalse();
        // 부상
        assertThat(eligible(s, "coach_007")).isFalse();
        s.injuredUntilDay = s.absoluteDay() + 7;
        assertThat(eligible(s, "coach_007")).isTrue();
        // 관계도와 플래그
        s.affinity.put(Axis.COACH, 50.0);
        assertThat(eligible(s, "coach_003")).isFalse();
        s.flags.add("met_coach");
        assertThat(eligible(s, "coach_003")).isTrue();
        s.affinity.put(Axis.COACH, 39.0);
        assertThat(eligible(s, "coach_003")).isFalse();
        // 체력 상한
        s.stamina = 60;
        assertThat(eligible(s, "common_002")).isFalse();
        s.stamina = 50;
        assertThat(eligible(s, "common_002")).isTrue();
        // 평판 하한
        assertThat(eligible(s, "common_003")).isFalse();
        s.reputation = 5;
        assertThat(eligible(s, "common_003")).isTrue();
    }

    @Test
    void lastMatchRoleTrigger() {
        GameState s = engine.newGame(3);
        s.week = engine.calendar().weekIndex(4, 1);
        s.day = Weekday.SAT;
        engine.apply(s, Action.Day.keep());
        if (engine.phase(s) == com.soccergame.domain.engine.Phase.MATCH) {
            engine.apply(s, new Action.ClutchChoice(s.pendingMatch.session.moment().id(), 0));
        }
        String role = s.lastMatch().role().name();
        assertThat(eligible(s, "coach_005")).isEqualTo(role.equals("SUB"));
        assertThat(eligible(s, "coach_006")).isEqualTo(role.equals("STARTER"));
    }

    @Test
    void triggeringSetsFlagsAndQueuesEvent() {
        GameState s = semester(1);
        s.week = 0;
        // 감독 축에서 1주차에 조건을 만족하는 것은 coach_001 뿐
        assertThat(events.eligible(s, Axis.COACH, EventSource.MEET)).extracting(EventDef::id).containsExactly("coach_001");
        events.trigger(s, Axis.COACH, EventSource.MEET);
        assertThat(s.flags).contains("met_coach");
        assertThat(s.pendingEvents.peekFirst().eventId()).isEqualTo("coach_001");
        assertThat(engine.phase(s)).isEqualTo(Phase.EVENT);
    }

    @Test
    void noEligibleEventMeansNothingHappens() {
        GameState s = semester(1);
        for (EventDef e : config.events().events()) {
            s.eventLastWeek.put(e.id(), s.week);
        }
        assertThat(events.trigger(s, null, EventSource.SUNDAY)).isEmpty();
        assertThat(s.pendingEvents).isEmpty();
    }

    @Test
    void pendingEventBlocksProgressUntilChosen() {
        GameState s = semester(2);
        events.trigger(s, Axis.FAMILY, EventSource.MEET);
        String id = s.pendingEvents.peekFirst().eventId();
        assertThatThrownBy(() -> engine.apply(s, Action.Day.keep())).isInstanceOf(InvalidActionException.class);
        assertThatThrownBy(() -> engine.apply(s, new Action.EventChoice("wrong", 0))).isInstanceOf(InvalidActionException.class);
        assertThatThrownBy(() -> engine.apply(s, new Action.EventChoice(id, 5))).isInstanceOf(InvalidActionException.class);
        engine.apply(s, new Action.EventChoice(id, 0));
        assertThat(engine.phase(s)).isEqualTo(Phase.WEEKDAY);
    }

    @Test
    void choiceAppliesEffectsWithClamping() {
        GameState s = semester(4);
        s.week = 0;
        s.stats.set("teamwork", 99.5);
        events.trigger(s, Axis.COACH, EventSource.MEET);
        double coach = s.affinity.get(Axis.COACH);
        engine.apply(s, new Action.EventChoice("coach_001", 1)); // 감독 +3, 팀워크 +1
        assertThat(s.affinity.get(Axis.COACH)).isEqualTo(coach + 3);
        assertThat(s.stats.get("teamwork")).isEqualTo(100);
    }

    @Test
    void meetingTriggersEventOfThatAxis() {
        for (long seed = 0; seed < 50; seed++) {
            GameState s = semester(seed);
            s.day = Weekday.SUN;
            engine.apply(s, new Action.Sunday(SundayActivity.MEET, Axis.FAMILY));
            assertThat(s.pendingEvents).isNotEmpty();
            assertThat(def(s.pendingEvents.peekFirst().eventId()).axis()).isEqualTo(Axis.FAMILY);
            assertThat(s.pendingEvents.peekFirst().source()).isEqualTo("MEET");
        }
    }

    @Test
    void sundayRandomEventAboutFiftyPercent() {
        int fired = 0;
        int n = 2000;
        for (long seed = 0; seed < n; seed++) {
            GameState s = semester(seed);
            s.day = Weekday.SUN;
            engine.apply(s, new Action.Sunday(SundayActivity.REST, null));
            fired += s.pendingEvents.size();
        }
        assertThat((double) fired / n).isBetween(0.46, 0.54);
    }

    @Test
    void classFriendsTriggersFriendEventAboutFifteenPercent() {
        int fired = 0;
        int n = 2000;
        for (long seed = 0; seed < n; seed++) {
            GameState s = semester(seed);
            engine.apply(s, new Action.Day(DawnChoice.SLEEP, ClassAttitude.FRIENDS, Map.of()));
            // 훈련 중 이벤트(5%)도 나올 수 있으므로 수업에서 나온 것만 센다
            s.pendingEvents.removeIf(p -> !p.source().equals("CLASS"));
            if (!s.pendingEvents.isEmpty()) {
                fired++;
                assertThat(def(s.pendingEvents.peekFirst().eventId()).axis()).isEqualTo(Axis.FRIEND);
                assertThat(s.pendingEvents.peekFirst().source()).isEqualTo("CLASS");
            }
        }
        assertThat((double) fired / n).isBetween(0.12, 0.18);
        // 집중·졸기·선생님께 질문은 수업 이벤트가 없다
        GameState s = semester(1);
        for (ClassAttitude a : List.of(ClassAttitude.FOCUS, ClassAttitude.DOZE, ClassAttitude.QUESTION)) {
            engine.apply(s, new Action.Day(null, a, Map.of()));
            assertThat(s.pendingEvents).noneMatch(p -> p.source().equals("CLASS"));
            s.pendingEvents.clear();
        }
    }

    @Test
    void questionAttitudeGivesAcademicsOnly() {
        GameState s = semester(2);
        double friend = s.affinity.get(Axis.FRIEND);
        engine.apply(s, new Action.Day(DawnChoice.SLEEP, ClassAttitude.QUESTION, Map.of()));
        assertThat(s.academics).isEqualTo(51.0);
        assertThat(s.affinity.get(Axis.FRIEND)).isEqualTo(friend);
    }

    @Test
    void introductionUnlocksGirlfriendOnlyWithContactChoice() {
        GameState s = semester(3);
        assertThat(eligible(s, "friend_007")).isFalse();
        assertThat(eligible(s, "girlfriend_002")).isFalse();
        s.affinity.put(Axis.FRIEND, 40.0);
        assertThat(eligible(s, "friend_007")).isTrue();

        GameState declined = semester(3);
        declined.affinity.put(Axis.FRIEND, 40.0);
        queue(declined, "friend_007");
        engine.apply(declined, new Action.EventChoice("friend_007", 1));
        assertThat(declined.affinity).doesNotContainKey(Axis.GIRLFRIEND);
        assertThat(declined.traitScores).containsEntry("hardWorker", 1);

        queue(s, "friend_007");
        ActionOutcome out = engine.apply(s, new Action.EventChoice("friend_007", 0));
        assertThat(s.affinity).containsEntry(Axis.GIRLFRIEND, 30.0);
        assertThat(s.axisUnlockedWeek).containsEntry(Axis.GIRLFRIEND, s.week);
        assertThat(out.log()).anyMatch(e -> e.slot().equals("알림") && e.text().contains("여자친구 축이 열렸다"));
        assertThat(eligible(s, "girlfriend_002")).isTrue();
        // 열린 뒤에는 여자친구를 만날 수 있고, 그 축 이벤트가 나온다
        s.day = Weekday.SUN;
        engine.apply(s, new Action.Sunday(SundayActivity.MEET, Axis.GIRLFRIEND));
        assertThat(s.affinity.get(Axis.GIRLFRIEND)).isGreaterThanOrEqualTo(30.0 + 5 - 4);
        assertThat(def(s.pendingEvents.peekFirst().eventId()).axis()).isEqualTo(Axis.GIRLFRIEND);
    }

    @Test
    void choosingTraitChoiceRaisesScore() {
        GameState s = semester(4);
        s.week = 0;
        events.trigger(s, Axis.COACH, EventSource.MEET);
        engine.apply(s, new Action.EventChoice("coach_001", 0));
        assertThat(s.traitScores).containsEntry("competitor", 1);
        GameState t = semester(4);
        t.week = 0;
        events.trigger(t, Axis.COACH, EventSource.MEET);
        engine.apply(t, new Action.EventChoice("coach_001", 2));
        assertThat(t.traitScores.values()).allMatch(v -> v == 0);
    }

    /** 이벤트를 대기열 맨 앞에 직접 넣는다 */
    private void queue(GameState s, String id) {
        s.pendingEvents.addFirst(new com.soccergame.domain.engine.PendingEvent(id, "TEST"));
    }

    @Test
    void eventEffectsCoverAllEffectKinds() {
        // 명세의 효과 항목(능력치, 체력, 컨디션, 돈, 학업, 관계도 4종, 평판)이 데이터에 모두 쓰였는지
        List<Events.Effects> all = config.events().events().stream()
                .flatMap(e -> e.choices().stream()).map(Events.Choice::effects).toList();
        assertThat(all).anyMatch(f -> f.stat() != null);
        assertThat(all).anyMatch(f -> f.stamina() != null);
        assertThat(all).anyMatch(f -> f.condition() != null);
        assertThat(all).anyMatch(f -> f.money() != null);
        assertThat(all).anyMatch(f -> f.academics() != null);
        assertThat(all).anyMatch(f -> f.coachAffinity() != null);
        assertThat(all).anyMatch(f -> f.teammateAffinity() != null);
        assertThat(all).anyMatch(f -> f.familyAffinity() != null);
        assertThat(all).anyMatch(f -> f.friendAffinity() != null);
        assertThat(all).anyMatch(f -> f.girlfriendAffinity() != null);
        assertThat(all).anyMatch(f -> f.unlockAxis() == Axis.GIRLFRIEND);
        assertThat(all).anyMatch(f -> f.reputation() != null);
    }

    @Test
    void trainingEventsOnlyFromTrainingSlotsAboutThreePercent() {
        int team = 0;
        int night = 0;
        int n = 3000;
        for (long seed = 0; seed < n; seed++) {
            GameState s = semester(seed);
            s.stamina = 90;
            engine.apply(s, new Action.Day(DawnChoice.SLEEP, ClassAttitude.DOZE, Map.of()));
            for (var p : s.pendingEvents) {
                if (p.source().equals("TEAM_TRAINING")) {
                    team++;
                    assertThat(def(p.eventId()).trigger().sources()).contains(EventSource.TEAM_TRAINING);
                }
                if (p.source().equals("NIGHT_TRAINING")) {
                    night++;
                    assertThat(def(p.eventId()).trigger().sources()).contains(EventSource.NIGHT_TRAINING);
                }
            }
        }
        assertThat((double) team / n).isBetween(0.018, 0.042);
        assertThat((double) night / n).isBetween(0.018, 0.042);
        // 다른 경로에서는 훈련 이벤트가 나오지 않는다
        GameState s = semester(1);
        assertThat(events.eligible(s, null, EventSource.SUNDAY))
                .noneMatch(e -> e.trigger() != null && e.trigger().sources() != null);
    }

    @Test
    void noTrainingEventOnInjuredOrMakeupSlots() {
        for (long seed = 0; seed < 500; seed++) {
            GameState s = semester(seed);
            s.injuredUntilDay = s.absoluteDay() + 7;
            s.makeupWeeks.put(s.week, 20.0);
            engine.apply(s, new Action.Day(DawnChoice.SLEEP, ClassAttitude.DOZE, Map.of()));
            assertThat(s.pendingEvents).noneMatch(p -> p.source().endsWith("TRAINING"));
        }
    }

    @Test
    void lastMatchConditionsAndSundayPriority() {
        GameState s = semester(7);
        assertThat(eligible(s, "match_001")).isFalse();
        // 직전 경기: 이번 주, 승리, 골 1
        var record = new com.soccergame.domain.match.MatchRecord(com.soccergame.domain.model.Competition.LEAGUE,
                "주말리그", null, "1라운드", 10, "x", 2, "상대", 50, "파이터형", true, com.soccergame.domain.model.MatchRole.STARTER, 40,
                1, 0, 1, 0, 0, 0, 2, 0, null, "W", 7.0, 0, null, null, java.util.List.of(), java.util.List.of(), null);
        s.week = 10;
        s.matches.add(record);
        assertThat(eligible(s, "match_001")).isTrue();   // 승리
        assertThat(eligible(s, "match_002")).isFalse();  // 패배 조건
        assertThat(eligible(s, "match_004")).isTrue();   // 공격 포인트 있음
        assertThat(eligible(s, "match_005")).isFalse();  // 공격 포인트 없음
        // 경기가 있던 주의 일요일 무작위 이벤트는 경기 전후 이벤트에서 고른다
        for (int i = 0; i < 20; i++) {
            GameState t = semester(7 + i);
            t.week = 10;
            t.matches.add(record);
            events.trigger(t, null, EventSource.SUNDAY);
            assertThat(def(t.pendingEvents.peekFirst().eventId()).trigger().isMatchEvent()).isTrue();
        }
        // 직전 경기가 2주 이내여야 한다 (경기 10주 → 12주까지)
        s.week = 12;
        assertThat(eligible(s, "match_001")).isTrue();
        s.week = 13;
        assertThat(eligible(s, "match_001")).isFalse();
    }
}
