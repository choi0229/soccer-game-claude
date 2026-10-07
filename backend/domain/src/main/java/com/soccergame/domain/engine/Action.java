package com.soccergame.domain.engine;

import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.ClassAttitude;
import com.soccergame.domain.model.DawnChoice;
import com.soccergame.domain.model.SundayActivity;
import com.soccergame.domain.model.TrainingSlot;

import java.util.Map;

/** 플레이어의 선택 하나. 이 순서만으로 판 전체를 재현할 수 있다. */
public sealed interface Action {

    /**
     * 월~토 하루 진행. 그날 바꾼 선택만 담는다(null 이면 기존 선택 유지).
     * 토요일에는 선택 변경만 저장되고 경기 또는 휴식으로 넘어간다.
     */
    record Day(DawnChoice dawn, ClassAttitude classAttitude, Map<TrainingSlot, String> menus) implements Action {
        public static Day keep() {
            return new Day(null, null, Map.of());
        }
    }

    /** 일요일 자유 행동. meetTarget 은 MEET 일 때만 쓴다. */
    record Sunday(SundayActivity activity, Axis meetTarget) implements Action {
    }

    /** 대기 중인 이벤트의 선택 */
    record EventChoice(String eventId, int choiceIndex) implements Action {
    }

    /** 멈춘 경기의 승부처 선택 */
    record ClutchChoice(String momentId, int choiceIndex) implements Action {
    }
}
