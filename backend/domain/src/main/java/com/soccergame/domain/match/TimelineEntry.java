package com.soccergame.domain.match;

import java.util.List;

/**
 * 중계 한 줄. score 는 이 줄까지의 스코어(우리-상대).
 * 내 장면(SCENE_SUCCESS / SCENE_FAIL / CLUTCH)에는 성공 확률과 보정 내역이 붙는다(그 밖은 null).
 */
public record TimelineEntry(int minute, String kind, String text, int ourScore, int theirScore, Double probability,
                            List<String> modifiers) {

    public TimelineEntry(int minute, String kind, String text, int ourScore, int theirScore) {
        this(minute, kind, text, ourScore, theirScore, null, null);
    }

    public boolean isMyScene() {
        return kind.equals("SCENE_SUCCESS") || kind.equals("SCENE_FAIL") || kind.equals("CLUTCH");
    }
}
