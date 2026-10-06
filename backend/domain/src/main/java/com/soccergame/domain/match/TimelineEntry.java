package com.soccergame.domain.match;

/** 중계 한 줄. score 는 이 시점 이후의 스코어(우리-상대). */
public record TimelineEntry(int minute, String kind, String text, int ourScore, int theirScore) {
}
