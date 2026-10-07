package com.soccergame.domain.match;

import com.soccergame.domain.config.ClutchMoments;

import java.util.List;

/** 승부처 선택지 하나의 미리보기. 확률은 지금 상황에서 고르면 판정에 쓰일 값과 같다. */
public record ClutchOption(int index, String text, ClutchMoments.Style style, List<String> stats, double probability,
                           List<String> modifiers, String reward, double failRating, String trait) {
}
