package com.soccergame.domain.event;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 이벤트가 나오는 곳. 이벤트 trigger 의 sources 로 제한할 수 있다. */
public enum EventSource {
    @JsonProperty("teamTraining") TEAM_TRAINING("팀 훈련"),
    @JsonProperty("nightTraining") NIGHT_TRAINING("야간 훈련"),
    @JsonProperty("sunday") SUNDAY("일요일"),
    @JsonProperty("meet") MEET("만남"),
    @JsonProperty("class") CLASS("수업 시간");

    private final String label;

    EventSource(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 훈련 중 이벤트는 sources 에 명시한 이벤트만 나온다 */
    public boolean requiresExplicitSource() {
        return this == TEAM_TRAINING || this == NIGHT_TRAINING;
    }
}
