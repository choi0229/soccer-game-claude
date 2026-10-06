package com.soccergame.domain.engine;

/** 선택을 기다리는 이벤트. source: SUNDAY / MEET / CLASS */
public record PendingEvent(String eventId, String source) {
}
