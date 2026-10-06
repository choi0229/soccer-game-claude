package com.soccergame.api.run;

import com.soccergame.domain.engine.Action;
import com.soccergame.domain.engine.InvalidActionException;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.ClassAttitude;
import com.soccergame.domain.model.DawnChoice;
import com.soccergame.domain.model.SundayActivity;
import com.soccergame.domain.model.TrainingSlot;

import java.util.Map;

/**
 * 행동 요청. 이 JSON 이 그대로 run_actions.payload 에 기록되고, 재현할 때 다시 읽힌다.
 * type: DAY (그날 바꾼 선택만) / SUNDAY / EVENT_CHOICE
 */
public record ActionRequest(Type type, DawnChoice dawn, ClassAttitude classAttitude, Map<TrainingSlot, String> menus,
                            SundayActivity activity, Axis meetTarget, String eventId, Integer choiceIndex) {

    public enum Type {
        DAY, SUNDAY, EVENT_CHOICE
    }

    public Action toAction() {
        if (type == null) {
            throw new InvalidActionException("type 이 필요합니다");
        }
        return switch (type) {
            case DAY -> new Action.Day(dawn, classAttitude, menus == null ? Map.of() : menus);
            case SUNDAY -> new Action.Sunday(activity, meetTarget);
            case EVENT_CHOICE -> {
                if (eventId == null || choiceIndex == null) {
                    throw new InvalidActionException("eventId 와 choiceIndex 가 필요합니다");
                }
                yield new Action.EventChoice(eventId, choiceIndex);
            }
        };
    }
}
