package com.soccergame.domain.engine;

import com.soccergame.domain.match.MatchRecord;

import java.util.List;

/** 행동 하나를 처리한 결과. 화면은 이것을 그대로 보여 준다. */
public record ActionOutcome(String dateLabel, List<LogEntry> log, MatchRecord match, List<String> newEvents) {
}
