package com.soccergame.domain.match;

import java.util.List;

/**
 * 플레이어 장면 하나의 판정 기록.
 * modifiers: 확률에 더해진 항목 설명 (예: "기세 +3", "돌파 보너스 +15").
 */
public record SceneLog(int minute, String sceneId, String sceneName, boolean extra, double probability,
                       boolean success, String result, String text, List<String> modifiers) {
}
