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
import static com.soccergame.domain.engine.EngineTestSupport.plainSemesterMonday;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ModifiersTest {
    private final Modifiers m = ENGINE.modifiers();

    @Test
    void traitTiers() {
        GameState s = plainSemesterMonday(1);
        int[][] cases = {{0, 0}, {9, 0}, {10, 1}, {24, 1}, {25, 2}, {44, 2}, {45, 3}, {60, 3}};
        for (int[] c : cases) {
            s.traitScores.put("competitor", c[0]);
            assertThat(m.traitTier(s, "competitor")).as("score %d", c[0]).isEqualTo(c[1]);
        }
    }

    @Test
    void bondTiersFollowCurrentAffinity() {
        GameState s = plainSemesterMonday(1);
        s.affinity.put(Axis.COACH, 39.9);
        assertThat(m.bondTier(s, Axis.COACH)).isZero();
        s.affinity.put(Axis.COACH, 60.0);
        assertThat(m.bondTier(s, Axis.COACH)).isEqualTo(2);
        s.affinity.put(Axis.COACH, 80.0);
        assertThat(m.bondTier(s, Axis.COACH)).isEqualTo(3);
        s.affinity.put(Axis.COACH, 59.0);
        assertThat(m.bondTier(s, Axis.COACH)).isEqualTo(1);
        assertThat(m.bondTier(s, Axis.GIRLFRIEND)).isEqualTo(-1);
    }

    @Test
    void trainingBonusMatchesSlotAndMenu() {
        GameState s = plainSemesterMonday(1);
        s.traitScores.put("hardWorker", 10);  // 야간 +5%
        s.traitScores.put("competitor", 25);  // 슈팅·파워·제공권 +10%
        s.traitScores.put("teamPlayer", 45);  // 연계·압박·돌파 +15%
        s.affinity.put(Axis.COACH, 40.0);     // 팀 훈련 +3%
        s.affinity.put(Axis.TEAMMATE, 60.0);  // 야간 +6%
        assertThat(m.training(s, TrainingSlot.AFTERNOON, "power").applied()).isCloseTo(0.13, within(1e-9));
        assertThat(m.training(s, TrainingSlot.NIGHT, "power").applied()).isCloseTo(0.05 + 0.10 + 0.06, within(1e-9));
        assertThat(m.training(s, TrainingSlot.MORNING, "link").applied()).isCloseTo(0.15 + 0.03, within(1e-9));
        assertThat(m.training(s, TrainingSlot.AFTERNOON, "fitness").applied()).isCloseTo(0.03, within(1e-9));
        // 여자친구 인연은 모든 훈련
        s.affinity.put(Axis.GIRLFRIEND, 80.0);
        assertThat(m.training(s, TrainingSlot.AFTERNOON, "fitness").applied()).isCloseTo(0.03 + 0.06, within(1e-9));
    }

    @Test
    void bonusIsCappedAtThirtyPercent() {
        GameState s = plainSemesterMonday(1);
        s.traitScores.put("hardWorker", 45);  // +15%
        s.traitScores.put("competitor", 45);  // +15%
        s.affinity.put(Axis.TEAMMATE, 80.0);  // +9%
        s.affinity.put(Axis.GIRLFRIEND, 80.0); // +6%
        Modifiers.TrainingBonus b = m.training(s, TrainingSlot.NIGHT, "shooting");
        assertThat(b.raw()).isCloseTo(0.45, within(1e-9));
        assertThat(b.applied()).isCloseTo(0.30, within(1e-9));
        assertThat(b.capped()).isTrue();
    }

    @Test
    void growthUsesBonusAndLogsBreakdown() {
        GameState s = plainSemesterMonday(2);
        s.stamina = 60;
        s.traitScores.put("competitor", 10);
        s.affinity.put(Axis.COACH, 40.0);
        double power = s.stats.get("shotPower");
        ActionOutcome out = ENGINE.apply(s, new Action.Day(DawnChoice.SLEEP, ClassAttitude.DOZE,
                Map.of(TrainingSlot.AFTERNOON, "power", TrainingSlot.NIGHT, "fitness")));
        // 0.09 × 주력 1.2 × (1 + 승부사 5% + 감독 인연 3%)
        assertThat(s.stats.get("shotPower") - power).isCloseTo(0.09 * 1.2 * 1.08, within(1e-9));
        assertThat(out.log()).filteredOn(e -> e.slot().equals("오후")).extracting(LogEntry::text).singleElement()
                .asString().contains("(승부사 +5%, 감독 인연 +3%)");
        assertThat(s.metrics.menuTrainings).isEqualTo(2);
        assertThat(s.metrics.bonusApplied).isCloseTo(0.08 + 0.0, within(1e-9));
    }

    @Test
    void modelStudentSlowsAcademicDecay() {
        GameState s = plainSemesterMonday(3);
        s.day = Weekday.SUN;
        s.academics = 50;
        s.traitScores.put("modelStudent", 25); // 4%
        ENGINE.apply(s, new Action.Sunday(SundayActivity.REST, null));
        assertThat(s.academics).isCloseTo(48, within(1e-9));
    }

    @Test
    void familyBondAddsNightRecovery() {
        GameState s = plainSemesterMonday(4);
        s.stamina = 40;
        s.affinity.put(Axis.FAMILY, 60.0); // +2
        ENGINE.apply(s, new Action.Day(DawnChoice.SLEEP, ClassAttitude.QUESTION, Map.of()));
        // 40 + 8(더 자기) - 8(오후) - 6(야간) + 8 + 2
        assertThat(s.stamina).isCloseTo(44, within(1e-9));
    }

    @Test
    void girlfriendDropsWhenNotMetOnSunday() {
        GameState s = plainSemesterMonday(5);
        s.affinity.put(Axis.GIRLFRIEND, 30.0);
        s.day = Weekday.SUN;
        ENGINE.apply(s, new Action.Sunday(SundayActivity.REST, null));
        assertThat(s.affinity.get(Axis.GIRLFRIEND)).isLessThanOrEqualTo(29.0 + 6); // 이벤트로 변할 수 있다
        GameState t = plainSemesterMonday(5);
        t.affinity.put(Axis.GIRLFRIEND, 30.0);
        t.day = Weekday.SUN;
        ActionOutcome out = ENGINE.apply(t, new Action.Sunday(SundayActivity.MEET, Axis.GIRLFRIEND));
        assertThat(out.log()).noneMatch(e -> e.slot().equals("관계"));
        GameState u = plainSemesterMonday(5);
        u.affinity.put(Axis.GIRLFRIEND, 30.0);
        u.day = Weekday.SUN;
        ActionOutcome rest = ENGINE.apply(u, new Action.Sunday(SundayActivity.PART_TIME, null));
        assertThat(rest.log()).anyMatch(e -> e.slot().equals("관계") && e.text().contains("관계도 -1"));
    }

    @Test
    void tierChangeIsAnnouncedInTheDayLog() {
        GameState s = plainSemesterMonday(6);
        s.affinity.put(Axis.FRIEND, 39.0);
        ActionOutcome out = ENGINE.apply(s, new Action.Day(null, ClassAttitude.FRIENDS, Map.of()));
        assertThat(s.affinity.get(Axis.FRIEND)).isEqualTo(40.0);
        assertThat(out.log()).anyMatch(e -> e.slot().equals("알림") && e.text().startsWith("학교 친구 인연 0단계 → 1단계"));
    }
}
