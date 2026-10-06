package com.soccergame.domain.model;

public enum DawnChoice {
    EXERCISE("개인 운동"), SLEEP("더 자기");

    private final String label;

    DawnChoice(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
