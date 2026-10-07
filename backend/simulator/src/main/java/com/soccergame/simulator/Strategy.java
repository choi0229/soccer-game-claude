package com.soccergame.simulator;

import com.soccergame.domain.config.Events.EventDef;
import com.soccergame.domain.engine.Action;
import com.soccergame.domain.engine.GameEngine;
import com.soccergame.domain.engine.GameState;
import com.soccergame.domain.match.ClutchOption;
import com.soccergame.domain.random.Rng;

import java.util.List;

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

    /** 승부처 선택지. 기본은 선택용 난수로 균등 무작위 */
    default int clutchChoice(GameState s, List<ClutchOption> options, Rng choice) {
        return choice.nextInt(options.size());
    }

    /** 성공 확률이 가장 높은 선택지 (같으면 앞쪽) */
    static int safest(List<ClutchOption> options) {
        int best = 0;
        for (int i = 1; i < options.size(); i++) {
            if (options.get(i).probability() > options.get(best).probability()) {
                best = i;
            }
        }
        return best;
    }

    /** 이벤트 선택지. 기본은 선택용 난수로 균등 무작위 */
    default int eventChoice(GameState s, EventDef event, Rng choice) {
        return choice.nextInt(event.choices().size());
    }
}
