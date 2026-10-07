package com.soccergame.domain.engine;

import com.soccergame.domain.match.MatchRecord;

import java.util.List;

/**
 * 행동 하나를 처리한 결과. 화면은 이것을 그대로 보여 준다.
 * partial 이면 경기가 승부처에서 멈춰 그날 처리가 아직 끝나지 않았다(이어지는 결과가 그날 전체 기록을 담는다).
 */
public record ActionOutcome(String dateLabel, List<LogEntry> log, MatchRecord match, List<String> newEvents,
                            boolean partial) {
    public ActionOutcome(String dateLabel, List<LogEntry> log, MatchRecord match, List<String> newEvents) {
        this(dateLabel, log, match, newEvents, false);
    }
}
