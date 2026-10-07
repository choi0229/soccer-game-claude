package com.soccergame.domain.model;

public enum Competition {
    LEAGUE("주말리그"), CUP("토너먼트");

    private final String label;

    Competition(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
