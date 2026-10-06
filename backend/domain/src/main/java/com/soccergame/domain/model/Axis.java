package com.soccergame.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 관계도와 이벤트의 축. common 은 이벤트 전용이며 관계도가 없다. */
public enum Axis {
    @JsonProperty("coach") COACH("감독"),
    @JsonProperty("teammate") TEAMMATE("동료"),
    @JsonProperty("family") FAMILY("가족"),
    @JsonProperty("friend") FRIEND("학교 친구"),
    /** 처음에는 잠겨 있고 이벤트로 열린다 */
    @JsonProperty("girlfriend") GIRLFRIEND("여자친구"),
    @JsonProperty("common") COMMON("공통");

    private final String label;

    Axis(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean hasAffinity() {
        return this != COMMON;
    }

    /** 처음에는 잠겨 있어 관계도가 없는 축 */
    public boolean lockedAtStart() {
        return this == GIRLFRIEND;
    }
}
