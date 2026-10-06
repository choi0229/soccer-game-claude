package com.soccergame.domain.calendar;

public enum Weekday {
    MON("월"), TUE("화"), WED("수"), THU("목"), FRI("금"), SAT("토"), SUN("일");

    private final String label;

    Weekday(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public boolean isWeekday() {
        return ordinal() <= FRI.ordinal();
    }
}
