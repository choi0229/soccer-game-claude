package com.soccergame.domain.model;

/** 가상의 학교. defenderType 은 핵심 수비수 유형 키(FIGHTER / COMMANDER). */
public record School(int id, String name, String typeKey, String typeName, int region, double strength,
                     String defenderType) {
}
