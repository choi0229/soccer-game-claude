package com.soccergame.domain.engine;

import com.soccergame.domain.calendar.Weekday;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.MatchRole;
import org.junit.jupiter.api.Test;

import static com.soccergame.domain.engine.EngineTestSupport.ENGINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClutchFlowTest {

    /** 리그 토요일, 선발로 뛰도록 만든 판에서 승부처가 나오는 시드를 찾는다 */
    private GameState pausedSaturday() {
        for (long seed = 0; seed < 200; seed++) {
            GameState s = ENGINE.newGame(seed);
            s.week = ENGINE.calendar().weekIndex(4, 1);
            s.day = Weekday.SAT;
            s.affinity.put(Axis.COACH, 100.0);
            ENGINE.apply(s, Action.Day.keep());
            if (ENGINE.phase(s) == Phase.MATCH) {
                return s;
            }
        }
        throw new AssertionError("승부처가 나오는 시드를 찾지 못했습니다");
    }

    @Test
    void saturdayMatchPausesAndResumes() {
        GameState s = pausedSaturday();
        assertThat(s.lastOutcome.partial()).isTrue();
        assertThat(s.day).isEqualTo(Weekday.SAT);
        assertThat(s.league.roundsPlayed()).isZero(); // 이번 라운드는 아직 기록 전
        assertThat(ENGINE.clutchOptions(s)).isNotEmpty();
        assertThat(ENGINE.liveTimeline(s).getFirst().kind()).isEqualTo("KICKOFF");
        // 승부처를 기다리는 동안 다른 행동은 받지 않는다
        assertThatThrownBy(() -> ENGINE.apply(s, Action.Day.keep())).isInstanceOf(InvalidActionException.class);
        assertThatThrownBy(() -> ENGINE.apply(s, new Action.ClutchChoice("nope", 0)))
                .isInstanceOf(InvalidActionException.class);
        String moment = s.pendingMatch.session.moment().id();
        assertThatThrownBy(() -> ENGINE.apply(s, new Action.ClutchChoice(moment, 9)))
                .isInstanceOf(InvalidActionException.class);

        ActionOutcome out = ENGINE.apply(s, new Action.ClutchChoice(moment, 0));
        assertThat(out.partial()).isFalse();
        assertThat(out.match()).isNotNull();
        assertThat(out.match().role()).isEqualTo(MatchRole.STARTER);
        assertThat(out.match().clutch().momentId()).isEqualTo(moment);
        assertThat(out.log()).anyMatch(e -> e.slot().equals("밤"));
        assertThat(s.pendingMatch).isNull();
        assertThat(s.day).isEqualTo(Weekday.SUN);
        assertThat(s.league.roundsPlayed()).isEqualTo(1);
    }

    @Test
    void clutchChoiceTraitIsCounted() {
        GameState s = pausedSaturday();
        var moment = s.pendingMatch.session.moment();
        String trait = moment.choices().get(0).trait();
        ENGINE.apply(s, new Action.ClutchChoice(moment.id(), 0));
        if (trait != null) {
            assertThat(s.traitScores.get(trait)).isEqualTo(1);
        }
    }

    @Test
    void weekdayCupMatchPauseKeepsRestOfDayForLater() {
        for (long seed = 0; seed < 300; seed++) {
            GameState s = ENGINE.newGame(seed);
            s.affinity.put(Axis.COACH, 100.0);
            EngineTestSupport.day(s); // 월
            EngineTestSupport.day(s); // 화
            EngineTestSupport.resolveEvents(s);
            ActionOutcome wed = ENGINE.apply(s, Action.Day.keep());
            if (ENGINE.phase(s) != Phase.MATCH) {
                continue;
            }
            assertThat(wed.log()).extracting(LogEntry::slot).contains("새벽", "오전");
            assertThat(wed.log()).noneMatch(e -> e.slot().equals("밤"));
            ActionOutcome done = ENGINE.apply(s, new Action.ClutchChoice(s.pendingMatch.session.moment().id(), 1));
            assertThat(done.log()).extracting(LogEntry::slot).contains("새벽", "오전", "오후·야간", "밤");
            assertThat(s.day).isEqualTo(Weekday.THU);
            assertThat(s.cups.get("spring").roundsPlayed()).isEqualTo(1);
            return;
        }
        throw new AssertionError("평일 승부처 시드를 찾지 못했습니다");
    }
}
