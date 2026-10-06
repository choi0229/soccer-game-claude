package com.soccergame.domain.engine;

import com.soccergame.domain.model.ClassAttitude;
import com.soccergame.domain.model.DawnChoice;
import com.soccergame.domain.model.TrainingSlot;

import java.util.EnumMap;
import java.util.Map;

/** 칸마다 저장되는 현재 선택. 바꾸기 전까지 유지된다. */
public final class Selections {
    public DawnChoice dawn;
    public ClassAttitude classAttitude;
    public final Map<TrainingSlot, String> menus = new EnumMap<>(TrainingSlot.class);
}
