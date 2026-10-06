package com.soccergame.domain.engine;

/** 일과 처리 기록 한 줄. slot 은 새벽·오전·오후·야간·경기·일요일·밤 등 표시용 이름. */
public record LogEntry(String slot, String text) {
}
