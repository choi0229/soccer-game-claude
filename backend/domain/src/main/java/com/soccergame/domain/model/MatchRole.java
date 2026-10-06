package com.soccergame.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum MatchRole {
    @JsonProperty("starter") STARTER("선발"),
    @JsonProperty("sub") SUB("교체"),
    @JsonProperty("bench") BENCH("벤치"),
    /** 부상으로 출전 불가 */
    @JsonProperty("absent") ABSENT("부상 결장");

    private final String label;

    MatchRole(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean plays() {
        return this == STARTER || this == SUB;
    }
}
