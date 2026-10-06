package com.soccergame.domain.config;

import java.util.List;

/** config/schools.yaml 의 구조. */
public record Schools(List<RegionDef> regions, List<SchoolTypeDef> types, List<DefenderTypeDef> defenderTypes,
                      PlayerSchool player, NameParts nameParts) {

    public record RegionDef(int id, String name) {
    }

    public record SchoolTypeDef(String key, String name, int count, double strength, String nameFormat) {
    }

    /** passive: 이 유형을 상대할 때 쓰는 패시브 능력치 키 */
    public record DefenderTypeDef(String key, String name, String passive) {
    }

    public record PlayerSchool(String schoolType, int region) {
    }

    public record NameParts(List<String> first, List<String> second) {
    }
}
