package com.soccergame.domain.engine;

/** 학업 점검 결과. remedialWeeks 는 보충수업이 걸린 주 수(범위 밖이면 0). */
public record AcademicCheckResult(String label, double academics, boolean failed, int remedialWeeks) {
}
