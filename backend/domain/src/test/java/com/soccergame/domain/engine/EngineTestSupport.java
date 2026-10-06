package com.soccergame.domain.engine;

import com.soccergame.domain.TestConfig;
import com.soccergame.domain.calendar.Weekday;
import com.soccergame.domain.model.SundayActivity;

/** 엔진 테스트 공용 도우미 */
final class EngineTestSupport {
    static final GameEngine ENGINE = new GameEngine(TestConfig.load());

    private EngineTestSupport() {
    }

    /** 경기도 방학도 없는 학기 중 월요일(10월 1주)로 옮긴 새 판 */
    static GameState plainSemesterMonday(long seed) {
        GameState s = ENGINE.newGame(seed);
        s.week = ENGINE.calendar().weekIndex(10, 1);
        s.day = Weekday.MON;
        return s;
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
                case EVENT -> resolveEvents(s);
                case WEEKDAY, SATURDAY -> ENGINE.apply(s, Action.Day.keep());
                case SUNDAY -> ENGINE.apply(s, new Action.Sunday(SundayActivity.REST, null));
                default -> throw new IllegalStateException();
            }
        }
        return s;
    }
}
