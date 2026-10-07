package com.soccergame.simulator;

import com.soccergame.domain.config.Events.EventDef;
import com.soccergame.domain.engine.Action;
import com.soccergame.domain.engine.GameEngine;
import com.soccergame.domain.engine.GameState;
import com.soccergame.domain.engine.PendingEvent;
import com.soccergame.domain.random.RandomStreams;
import com.soccergame.domain.random.Rng;

/** 화면 없이 한 판(1년)을 끝까지 돈다. */
public final class YearRunner {
    private final GameEngine engine;

    public YearRunner(GameEngine engine) {
        this.engine = engine;
    }

    public GameState run(long seed, Strategy strategy) {
        GameState s = engine.newGame(seed);
        Rng choice = RandomStreams.choiceStream(seed);
        while (true) {
            switch (engine.phase(s)) {
                case FINISHED -> {
                    return s;
                }
                case MATCH -> {
                    var options = engine.clutchOptions(s);
                    engine.apply(s, new Action.ClutchChoice(s.pendingMatch.session.moment().id(),
                            strategy.clutchChoice(s, options, choice)));
                }
                case EVENT -> {
                    PendingEvent pending = s.pendingEvents.peekFirst();
                    EventDef def = engine.config().event(pending.eventId()).orElseThrow();
                    engine.apply(s, new Action.EventChoice(def.id(), strategy.eventChoice(s, def, choice)));
                }
                case WEEKDAY, SATURDAY -> engine.apply(s, strategy.day(s, engine, choice));
                case SUNDAY -> engine.apply(s, strategy.sunday(s, engine, choice));
            }
        }
    }
}
