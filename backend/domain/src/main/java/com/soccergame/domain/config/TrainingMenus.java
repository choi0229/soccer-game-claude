package com.soccergame.domain.config;

import com.soccergame.domain.model.TrainingSlot;

import java.util.List;
import java.util.Map;

/** config/training-menus.yaml 의 구조. */
public record TrainingMenus(List<TrainingMenu> menus, Map<TrainingSlot, String> defaults) {

    /**
     * 훈련 메뉴 하나. stats 의 능력치를 각각 성장시킨다.
     * unlock 은 해금 조건 자리이며, 비어 있으면 처음부터 쓸 수 있다.
     */
    public record TrainingMenu(String id, String name, List<String> stats, double growthMultiplier,
                               Map<String, Object> unlock) {
        public boolean unlocked() {
            return unlock == null || unlock.isEmpty();
        }
    }
}
