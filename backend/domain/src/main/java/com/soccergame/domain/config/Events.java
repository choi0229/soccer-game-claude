package com.soccergame.domain.config;

import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.MatchRole;

import java.util.List;
import java.util.Map;

/** config/events.yaml 의 구조. */
public record Events(List<EventDef> events) {

    public record EventDef(String id, Axis axis, String title, String body, Trigger trigger, List<Choice> choices,
                           List<String> setFlags, boolean once) {
        public Trigger triggerOrEmpty() {
            return trigger == null ? Trigger.NONE : trigger;
        }

        public List<String> flagsOrEmpty() {
            return setFlags == null ? List.of() : setFlags;
        }
    }

    /**
     * 발생 조건. 비어 있는 필드는 검사하지 않는다.
     * minWeek / maxWeek 는 1년 기준 1~48 주차.
     */
    public record Trigger(Integer minWeek, Integer maxWeek, Boolean vacation, Boolean injured,
                          MatchRole lastMatchRole,
                          Double coachAffinityMin, Double coachAffinityMax,
                          Double teammateAffinityMin, Double teammateAffinityMax,
                          Double familyAffinityMin, Double familyAffinityMax,
                          Double friendAffinityMin, Double friendAffinityMax,
                          Double girlfriendAffinityMin, Double girlfriendAffinityMax,
                          List<Axis> unlockedAxes,
                          Double staminaMin, Double staminaMax,
                          Integer conditionMin, Integer conditionMax,
                          Double academicsMin, Double academicsMax,
                          Long moneyMin, Double reputationMin,
                          List<String> requiresFlags, List<String> excludesFlags) {
        public static final Trigger NONE = new Trigger(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    /** trait: 이 선택지를 고르면 점수가 1 오르는 특성 키 (없으면 null) */
    public record Choice(String text, Effects effects, String trait) {
    }

    /** 효과. 능력치(stat)는 능력치 키 → 변화량. unlockAxis 는 잠긴 축을 연다. */
    public record Effects(Map<String, Double> stat, Double stamina, Integer condition, Long money, Double academics,
                          Double coachAffinity, Double teammateAffinity, Double familyAffinity,
                          Double friendAffinity, Double girlfriendAffinity, Double reputation, Axis unlockAxis) {
        public static final Effects NONE = new Effects(null, null, null, null, null, null, null, null, null, null,
                null, null);
    }
}
