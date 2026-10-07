package com.soccergame.domain.match;

import com.soccergame.domain.config.ClutchMoments;

/**
 * 승부처에서 고른 선택과 결과.
 * ratingChange: 이 선택 자체의 평점 변화 (성공: 골 +1.0 / 도움 +0.7 / 그 밖 +0.3, 실패: 선택지의 실패 평점).
 * 슈팅 장면 추가로 이어진 장면의 판정은 포함하지 않는다.
 */
public record ClutchLog(String momentId, String title, String situation, int minute, int choiceIndex,
                        String choiceText, ClutchMoments.Style style, double probability, boolean success,
                        String result, double ratingChange) {
}
