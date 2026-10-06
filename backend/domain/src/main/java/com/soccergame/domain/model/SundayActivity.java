package com.soccergame.domain.model;

public enum SundayActivity {
    REST("휴식"), PART_TIME("아르바이트"), MEET("사람 만나기");

    private final String label;

    SundayActivity(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
