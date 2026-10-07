package com.soccergame.domain.engine;

import com.soccergame.domain.TestConfig;
import com.soccergame.domain.calendar.Weekday;
import com.soccergame.domain.model.SundayActivity;

/** 엔진 테스트 공용 도우미 */
final class EngineTestSupport {
    static final GameEngine ENGINE = new GameEngine(TestConfig.load());
    /** 가족 관계도가 50에서 시작해 처음부터 가족 인연 1단계: 밤 회복 +1 */
    static final double FAMILY_BOND = 1;

    private EngineTestSupport() {
    }

    /** 경기도 방학도 없는 학기 중 월요일(11월 1주, 대회 기간 밖)로 옮긴 새 판 */
    static GameState plainSemesterMonday(long seed) {
        GameState s = ENGINE.newGame(seed);
        s.week = ENGINE.calendar().weekIndex(11, 1);
        s.day = Weekday.MON;
        return s;
    }

    /** 행동을 적용하고, 경기가 승부처에서 멈추면 첫 선택지로 끝까지 진행한다. 마지막 결과를 돌려준다. */
    static ActionOutcome act(GameState s, Action action) {
        ActionOutcome out = ENGINE.apply(s, action);
        while (ENGINE.phase(s) == Phase.MATCH) {
            out = ENGINE.apply(s, new Action.ClutchChoice(s.pendingMatch.session.moment().id(), 0));
        }
        return out;
    }

    /** 대기 중인 이벤트를 첫 선택지로 처리하고 하루(Day.keep)를 진행한다. 승부처는 첫 선택지. */
    static ActionOutcome day(GameState s) {
        resolveEvents(s);
        return act(s, Action.Day.keep());
    }

    static void resolveEvents(GameState s) {
        while (!s.pendingEvents.isEmpty()) {
            ENGINE.apply(s, new Action.EventChoice(s.pendingEvents.peekFirst().eventId(), 0));
        }
    }

    /** 고정 선택으로 1년을 끝까지 돈다 */
    static GameState playYear(long seed) {
        GameState s = ENGINE.newGame(seed);
        while (ENGINE.phase(s) != Phase.FINISHED) {
            switch (ENGINE.phase(s)) {
                case MATCH -> ENGINE.apply(s, new Action.ClutchChoice(s.pendingMatch.session.moment().id(), 0));
                case EVENT -> resolveEvents(s);
                case WEEKDAY, SATURDAY -> ENGINE.apply(s, Action.Day.keep());
                case SUNDAY -> ENGINE.apply(s, new Action.Sunday(SundayActivity.REST, null));
                default -> throw new IllegalStateException();
            }
        }
        return s;
    }
}
