package com.soccergame.domain.engine;

/** 다음에 받을 행동의 종류 */
public enum Phase {
    /** 경기 중 승부처 선택 대기 (이벤트보다 먼저) */
    MATCH,
    /** 이벤트 선택 대기 */
    EVENT,
    /** 월~금 일과 */
    WEEKDAY,
    /** 토요일 (경기일 또는 휴식) */
    SATURDAY,
    /** 일요일 자유 행동 */
    SUNDAY,
    /** 48주 종료 */
    FINISHED
}
