package com.soccergame.domain.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.soccergame.domain.event.EventSource;
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
     * sources: 이 이벤트가 나올 수 있는 곳. 비어 있으면 일요일·만남·수업(기존 경로)에서 나오고 훈련 중에는 나오지 않는다.
     * lastMatchResult / lastMatchContributed: 직전 경기 결과, 직전 경기 골·도움 기록 여부.
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
                          List<String> requiresFlags, List<String> excludesFlags,
                          List<EventSource> sources, List<MatchResult> lastMatchResult,
                          Boolean lastMatchContributed) {
        public static final Trigger NONE = new Trigger(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null);

        /** 직전 경기 결과·공격 포인트 조건이 있는 경기 전후 이벤트 */
        public boolean isMatchEvent() {
            return lastMatchResult != null || lastMatchContributed != null;
        }
    }

    /** 직전 경기 결과 (승부차기로 갈린 경기는 무승부) */
    public enum MatchResult {
        @JsonProperty("win") WIN("W"),
        @JsonProperty("draw") DRAW("D"),
        @JsonProperty("loss") LOSS("L");

        private final String code;

        MatchResult(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }
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
