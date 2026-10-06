package com.soccergame.domain.engine;

import com.soccergame.domain.calendar.Weekday;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.ClassAttitude;
import com.soccergame.domain.model.DawnChoice;
import com.soccergame.domain.model.SundayActivity;
import com.soccergame.domain.model.TrainingSlot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.soccergame.domain.engine.EngineTestSupport.ENGINE;
import static com.soccergame.domain.engine.EngineTestSupport.FAMILY_BOND;
import static com.soccergame.domain.engine.EngineTestSupport.plainSemesterMonday;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class DayProcessorTest {

    @Test
    void semesterWeekdayRunsFourSlotsInOrder() {
        GameState s = plainSemesterMonday(1);
        s.stamina = 60;
        double finishing = s.stats.get("finishing");
        double composure = s.stats.get("composure");
        double power = s.stats.get("shotPower");
        double kick = s.stats.get("kickPower");
        ActionOutcome out = ENGINE.apply(s, new Action.Day(DawnChoice.SLEEP, ClassAttitude.FOCUS,
                Map.of(TrainingSlot.AFTERNOON, "shooting", TrainingSlot.NIGHT, "power")));

        assertThat(out.log()).extracting(LogEntry::slot).containsExactly("새벽", "오전", "오후", "야간", "밤");
        // 수업 집중: 1.5 × (1 + 30/200)
        assertThat(s.academics).isCloseTo(50 + 1.5 * 1.15, within(1e-9));
        // 오후 슈팅(팀 0.09), 야간 파워(개인 0.06, 주력 1.2배)
        assertThat(s.stats.get("finishing") - finishing).isCloseTo(0.09, within(1e-9));
        assertThat(s.stats.get("composure") - composure).isCloseTo(0.09, within(1e-9));
        assertThat(s.stats.get("shotPower") - power).isCloseTo(0.072, within(1e-9));
        assertThat(s.stats.get("kickPower") - kick).isCloseTo(0.072, within(1e-9));
        // 체력: 60 + 8 - 2 - 8 - 6 + 8 + 가족 인연
        assertThat(s.stamina).isCloseTo(60 + FAMILY_BOND, within(1e-9));
        assertThat(s.day).isEqualTo(Weekday.TUE);
        assertThat(s.menuTrainingCounts).containsEntry("shooting", 1).containsEntry("power", 1);
    }

    @Test
    void vacationMorningIsTeamTraining() {
        GameState s = ENGINE.newGame(1);
        s.week = ENGINE.calendar().weekIndex(8, 1);
        double heading = s.stats.get("heading");
        ActionOutcome out = ENGINE.apply(s, new Action.Day(DawnChoice.SLEEP, null,
                Map.of(TrainingSlot.MORNING, "aerial", TrainingSlot.AFTERNOON, "shooting", TrainingSlot.NIGHT, "shooting")));
        assertThat(out.log().get(1).text()).startsWith("팀 훈련 [제공권]");
        assertThat(s.stats.get("heading") - heading).isCloseTo(0.108, within(1e-9));
        assertThat(s.academics).isEqualTo(50);
    }

    @Test
    void menusAndChoicesPersistUntilChanged() {
        GameState s = plainSemesterMonday(2);
        ENGINE.apply(s, new Action.Day(DawnChoice.EXERCISE, ClassAttitude.DOZE, Map.of(TrainingSlot.NIGHT, "pressing")));
        ENGINE.apply(s, Action.Day.keep());
        assertThat(s.selections.dawn).isEqualTo(DawnChoice.EXERCISE);
        assertThat(s.selections.classAttitude).isEqualTo(ClassAttitude.DOZE);
        assertThat(s.selections.menus.get(TrainingSlot.NIGHT)).isEqualTo("pressing");
        assertThat(s.selections.menus.get(TrainingSlot.AFTERNOON)).isEqualTo("shooting");
        assertThat(s.menuTrainingCounts.get("pressing")).isEqualTo(2);
    }

    @Test
    void exhaustionExcludesRestOfDayAndCostsCoachAffinity() {
        GameState s = plainSemesterMonday(3);
        s.stamina = 9;
        double finishing = s.stats.get("finishing");
        // 새벽에 더 자면 17, 졸기 +8 → 25, 오후 -8 → 17, 야간 -6 → 11. 고갈 없음
        ENGINE.apply(s, new Action.Day(DawnChoice.SLEEP, ClassAttitude.DOZE, null));
        assertThat(s.metrics.exclusions).isZero();

        s.stamina = 12;
        // 새벽 운동 -6 → 6, 수업 집중 -2 → 4, 오후 시작 시 4 < 10 → 제외
        ActionOutcome out = ENGINE.apply(s, new Action.Day(DawnChoice.EXERCISE, ClassAttitude.FOCUS, null));
        assertThat(s.metrics.exclusions).isEqualTo(1);
        assertThat(s.affinity.get(Axis.COACH)).isEqualTo(28);
        assertThat(out.log().get(2).text()).contains("제외");
        assertThat(out.log().get(3).text()).contains("제외");
        assertThat(s.excludedToday).isTrue();
        // 첫날 오후 팀 훈련 0.09 + 야간 개인 훈련 0.06 (둘째 날은 제외되어 성장 없음)
        assertThat(s.stats.get("finishing") - finishing).isCloseTo(0.15, within(1e-9));
    }

    @Test
    void injuredPlayerRehabsWithoutGrowthOrStaminaCost() {
        GameState s = plainSemesterMonday(4);
        s.injuredUntilDay = s.absoluteDay() + 7;
        s.stamina = 50;
        var before = s.stats.copy();
        ActionOutcome out = ENGINE.apply(s, new Action.Day(DawnChoice.EXERCISE, ClassAttitude.FOCUS, null));
        assertThat(out.log().get(0).text()).contains("부상");
        assertThat(out.log()).filteredOn(e -> e.slot().equals("오후") || e.slot().equals("야간"))
                .extracting(LogEntry::text).allMatch(t -> t.startsWith("재활"));
        assertThat(s.stats.get("finishing")).isEqualTo(before.get("finishing"));
        assertThat(s.stats.get("fitness")).isEqualTo(before.get("fitness"));
        // 50 + 8(운동 대신 더 자기) - 2(수업) + 8 + 가족 인연
        assertThat(s.stamina).isEqualTo(64 + FAMILY_BOND);
    }

    @Test
    void injuryRateIsAboutThreePercentPerSlotBelowThirty() {
        int slots = 0;
        int injuries = 0;
        for (long seed = 0; seed < 3000; seed++) {
            GameState s = plainSemesterMonday(seed);
            s.stamina = 29;
            ENGINE.apply(s, new Action.Day(DawnChoice.SLEEP, ClassAttitude.DOZE, null));
            // 더 자기 37, 졸기 45 → 오후는 위험 구간이 아님. 야간도 37 이라 아님.
            assertThat(s.metrics.injuries).isZero();
            s = plainSemesterMonday(seed);
            s.stamina = 29;
            s.selections.dawn = DawnChoice.EXERCISE;
            s.selections.classAttitude = ClassAttitude.QUESTION;
            ENGINE.apply(s, Action.Day.keep());
            // 새벽 29(위험) → 23, 오후 23(위험) → 15, 야간 15(위험): 부상이 나면 그 뒤는 재활
            slots += 3;
            injuries += s.metrics.injuries;
        }
        double rate = (double) injuries / slots;
        assertThat(rate).isBetween(0.02, 0.04);
    }

    @Test
    void sundayRestRecoversAndRaisesCondition() {
        GameState s = plainSemesterMonday(5);
        s.day = Weekday.SUN;
        s.stamina = 20;
        int week = s.week;
        ENGINE.apply(s, new Action.Sunday(SundayActivity.REST, null));
        assertThat(s.stamina).isEqualTo(Math.min(20 + 25 + 8 + FAMILY_BOND + 20, ENGINE.resources().maxStamina(s)));
        assertThat(s.condition).isEqualTo(3);
        assertThat(s.week).isEqualTo(week + 1);
        assertThat(s.day).isEqualTo(Weekday.MON);
    }

    @Test
    void sundayPartTimeAndMeet() {
        GameState s = plainSemesterMonday(6);
        s.day = Weekday.SUN;
        ENGINE.apply(s, new Action.Sunday(SundayActivity.PART_TIME, null));
        assertThat(s.money).isEqualTo(30000);
        EngineTestSupport.resolveEvents(s);

        s.day = Weekday.SUN;
        double family = s.affinity.get(Axis.FAMILY);
        ENGINE.apply(s, new Action.Sunday(SundayActivity.MEET, Axis.FAMILY));
        assertThat(s.affinity.get(Axis.FAMILY)).isGreaterThanOrEqualTo(family + 5 - 6);
        assertThatThrownBy(() -> {
            GameState t = plainSemesterMonday(6);
            t.day = Weekday.SUN;
            ENGINE.apply(t, new Action.Sunday(SundayActivity.MEET, Axis.FRIEND));
        }).isInstanceOf(InvalidActionException.class);
        // 잠긴 여자친구 축은 만날 수 없다
        assertThatThrownBy(() -> {
            GameState t = plainSemesterMonday(6);
            t.day = Weekday.SUN;
            ENGINE.apply(t, new Action.Sunday(SundayActivity.MEET, Axis.GIRLFRIEND));
        }).isInstanceOf(InvalidActionException.class);
    }

    @Test
    void saturdayWithLowStaminaDropsCondition() {
        GameState s = plainSemesterMonday(7);
        s.day = Weekday.SAT;
        s.stamina = 25;
        ENGINE.apply(s, Action.Day.keep());
        assertThat(s.condition).isEqualTo(1);
        assertThat(s.matches).isEmpty();
        assertThat(s.day).isEqualTo(Weekday.SUN);
    }

    @Test
    void endingWeekBelowThirtyTurnsNextWeekAfternoonsIntoMakeupStudy() {
        GameState s = plainSemesterMonday(8);
        s.day = Weekday.SUN;
        s.academics = 31;
        int week = s.week;
        // 학기 중 감소(31 × 6%) 뒤 29.14 < 30 → 다음 주 나머지 공부
        ENGINE.apply(s, new Action.Sunday(SundayActivity.REST, null));
        assertThat(s.makeupWeeks).containsOnlyKeys(week + 1);
        assertThat(s.makeupWeeks.get(week + 1)).isCloseTo(31 * 0.94, within(1e-9));

        EngineTestSupport.resolveEvents(s);
        s.academics = 20;
        double finishing = s.stats.get("finishing");
        double stamina = s.stamina = 60;
        ActionOutcome out = ENGINE.apply(s, new Action.Day(DawnChoice.SLEEP, ClassAttitude.DOZE,
                Map.of(TrainingSlot.AFTERNOON, "shooting", TrainingSlot.NIGHT, "power")));
        assertThat(out.log()).filteredOn(e -> e.slot().equals("오후")).extracting(LogEntry::text)
                .singleElement().asString().startsWith("나머지 공부");
        assertThat(s.stats.get("finishing")).isEqualTo(finishing);
        assertThat(s.academics).isCloseTo(20 - 0.5 + 2, within(1e-9));
        // 더 자기 +8, 졸기 +8, 나머지 공부 -8, 야간 -6, 밤 +8
        assertThat(s.stamina).isEqualTo(stamina + 8 + 8 - 8 - 6 + 8 + FAMILY_BOND);
        assertThat(s.metrics.makeupDays).isEqualTo(1);
    }

    @Test
    void makeupAppliesInVacationAndWhileInjured() {
        GameState s = ENGINE.newGame(9);
        s.week = ENGINE.calendar().weekIndex(8, 2);
        s.day = Weekday.SUN;
        s.academics = 25;
        ENGINE.apply(s, new Action.Sunday(SundayActivity.REST, null));
        // 방학 주에는 감소가 없지만 25 < 30 이라 다음 주(방학)도 나머지 공부
        assertThat(s.makeupWeeks).containsEntry(s.week, 25.0);
        EngineTestSupport.resolveEvents(s);
        s.injuredUntilDay = s.absoluteDay() + 14;
        ActionOutcome out = ENGINE.apply(s, Action.Day.keep());
        assertThat(out.log()).filteredOn(e -> e.slot().equals("오전")).extracting(LogEntry::text)
                .singleElement().asString().startsWith("재활");
        assertThat(out.log()).filteredOn(e -> e.slot().equals("오후")).extracting(LogEntry::text)
                .singleElement().asString().startsWith("나머지 공부");
    }

    @Test
    void matchDayAfternoonOverridesMakeup() {
        GameState s = ENGINE.newGame(10);
        s.makeupWeeks.put(0, 20.0);
        ENGINE.apply(s, Action.Day.keep()); // 월: 나머지 공부
        ENGINE.apply(s, Action.Day.keep()); // 화
        EngineTestSupport.resolveEvents(s);
        ActionOutcome wed = ENGINE.apply(s, Action.Day.keep()); // 수: 춘계배 경기
        assertThat(wed.match()).isNotNull();
        assertThat(wed.log()).noneMatch(e -> e.text().startsWith("나머지 공부"));
        assertThat(s.metrics.makeupDays).isEqualTo(2);
    }

    @Test
    void noMakeupAfterTheLastWeek() {
        GameState s = ENGINE.newGame(11);
        s.week = ENGINE.calendar().weeksPerYear() - 1;
        s.day = Weekday.SUN;
        s.academics = 5;
        ENGINE.apply(s, new Action.Sunday(SundayActivity.REST, null));
        assertThat(s.makeupWeeks).isEmpty();
        assertThat(s.finished).isTrue();
    }

    @Test
    void sundayLogBreaksDownRecovery() {
        GameState s = plainSemesterMonday(13);
        s.day = Weekday.SUN;
        s.stamina = 20;
        ActionOutcome out = ENGINE.apply(s, new Action.Sunday(SundayActivity.REST, null));
        assertThat(out.log()).filteredOn(e -> e.slot().equals("밤")).extracting(LogEntry::text).singleElement()
                .asString().startsWith("체력 휴식 +25, 밤 회복 +8, 가족 인연 +1, 일요일 추가 회복 +20, 합계 +54");

        GameState full = plainSemesterMonday(13);
        full.day = Weekday.SUN;
        ActionOutcome capped = ENGINE.apply(full, new Action.Sunday(SundayActivity.PART_TIME, null));
        assertThat(capped.log()).filteredOn(e -> e.slot().equals("밤")).extracting(LogEntry::text).singleElement()
                .asString().contains("아르바이트 -10").contains("합계 +19").contains("제한으로 실제");
    }

    @Test
    void academicsDropEverySemesterWeekButNotInVacation() {
        GameState s = plainSemesterMonday(11);
        s.day = Weekday.SUN;
        s.academics = 50;
        ENGINE.apply(s, new Action.Sunday(SundayActivity.REST, null));
        assertThat(s.academics).isCloseTo(47, within(1e-9));
        GameState t = plainSemesterMonday(11);
        t.day = Weekday.SUN;
        t.academics = 80;
        ENGINE.apply(t, new Action.Sunday(SundayActivity.REST, null));
        assertThat(t.academics).isCloseTo(80 * 0.94, within(1e-9));

        GameState v = ENGINE.newGame(11);
        v.week = ENGINE.calendar().weekIndex(8, 2);
        v.day = Weekday.SUN;
        v.academics = 50;
        ENGINE.apply(v, new Action.Sunday(SundayActivity.REST, null));
        assertThat(v.academics).isEqualTo(50);
    }

    @Test
    void vacationNightRecoveryIsTwelve() {
        GameState s = ENGINE.newGame(12);
        s.week = ENGINE.calendar().weekIndex(8, 1);
        s.stamina = 60;
        // 더 자기 +8, 오전·오후 팀 훈련 -8 -8, 야간 -6, 방학 밤 회복 +12
        ENGINE.apply(s, new Action.Day(DawnChoice.SLEEP, null, null));
        assertThat(s.stamina).isCloseTo(60 + 8 - 8 - 8 - 6 + 12 + FAMILY_BOND, within(1e-9));

        s.day = Weekday.SUN;
        s.stamina = 20;
        ENGINE.apply(s, new Action.Sunday(SundayActivity.PART_TIME, null));
        // 아르바이트 -10, 방학 밤 회복 +12, 일요일 추가 +20
        assertThat(s.stamina).isCloseTo(20 - 10 + 12 + FAMILY_BOND + 20, within(1e-9));
    }

    @Test
    void sundayRandomEventSeesStaminaBeforeNightRecovery() {
        // 몸살 기운(common_002)은 체력 50 이하에서만 나온다. 아르바이트 뒤 체력 20 → 회복 전에 뽑으므로 나올 수 있다.
        int fired = 0;
        for (long seed = 0; seed < 400; seed++) {
            GameState s = plainSemesterMonday(seed);
            s.day = Weekday.SUN;
            s.stamina = 30;
            ENGINE.apply(s, new Action.Sunday(SundayActivity.PART_TIME, null));
            if (s.eventHistory.contains("common_002")) {
                fired++;
            }
        }
        assertThat(fired).isPositive();
    }

    @Test
    void wrongActionForPhaseIsRejectedWithoutChangingState() {
        GameState s = plainSemesterMonday(10);
        assertThatThrownBy(() -> ENGINE.apply(s, new Action.Sunday(SundayActivity.REST, null)))
                .isInstanceOf(InvalidActionException.class);
        assertThatThrownBy(() -> ENGINE.apply(s, new Action.Day(null, null, Map.of(TrainingSlot.NIGHT, "nope"))))
                .isInstanceOf(InvalidActionException.class);
        assertThat(s.actionCount).isZero();
        assertThat(s.day).isEqualTo(Weekday.MON);
    }
}
