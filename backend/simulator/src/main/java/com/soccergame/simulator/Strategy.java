package com.soccergame.simulator;

import com.soccergame.domain.config.Events.EventDef;
import com.soccergame.domain.engine.Action;
import com.soccergame.domain.engine.GameEngine;
import com.soccergame.domain.engine.GameState;
import com.soccergame.domain.random.Rng;

/**
 * 시뮬레이터 전략. 선택에는 선택용 난수(choice)만 쓴다.
 * 이벤트 선택지는 따로 정하지 않으면 선택용 난수로 균등 무작위로 고른다.
 */
public interface Strategy {
    String name();

    /** 월~토 하루 */
    Action.Day day(GameState s, GameEngine engine, Rng choice);

    /** 일요일 */
    Action.Sunday sunday(GameState s, GameEngine engine, Rng choice);

    /** 이벤트 선택지. 기본은 선택용 난수로 균등 무작위 */
    default int eventChoice(GameState s, EventDef event, Rng choice) {
        return choice.nextInt(event.choices().size());
    }
}
