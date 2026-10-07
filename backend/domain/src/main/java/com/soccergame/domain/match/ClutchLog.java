package com.soccergame.domain.match;

import com.soccergame.domain.config.ClutchMoments;

/** 승부처에서 고른 선택과 결과 */
public record ClutchLog(String momentId, String title, String situation, int minute, int choiceIndex,
                        String choiceText, ClutchMoments.Style style, double probability, boolean success,
                        String result) {
}
