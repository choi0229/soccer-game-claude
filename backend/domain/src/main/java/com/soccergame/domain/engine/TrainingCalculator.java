package com.soccergame.domain.engine;

import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.config.Rules;
import com.soccergame.domain.config.TrainingMenus.TrainingMenu;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 훈련 성장량 계산. 메뉴 데이터(능력치 목록, 배율)만 보고 계산하므로 메뉴가 늘어도 바뀌지 않는다.
 * 1회 성장량(능력치마다) = 기준값 × 메뉴 배율 × 유형 보정 × 컨디션 배율 × 감쇠 × (1 + 보너스)
 * 보너스 = min(특성 보너스 + 인연 보너스, 상한)
 */
public final class TrainingCalculator {
    private final GameConfig config;

    public TrainingCalculator(GameConfig config) {
        this.config = config;
    }

    public double growth(String statKey, double currentValue, double baseGrowth, double menuMultiplier,
                         double conditionMultiplier) {
        return growth(statKey, currentValue, baseGrowth, menuMultiplier, conditionMultiplier, 0);
    }

    public double growth(String statKey, double currentValue, double baseGrowth, double menuMultiplier,
                         double conditionMultiplier, double bonus) {
        double archetype = config.isPrimary(statKey) ? config.rules().archetype().primaryGrowthMultiplier() : 1.0;
        return baseGrowth * menuMultiplier * archetype * conditionMultiplier * decay(currentValue) * (1 + bonus);
    }

    public double decay(double value) {
        for (Rules.DecayStep step : config.rules().training().decay()) {
            if (value < step.below()) {
                return step.factor();
            }
        }
        return config.rules().training().decay().getLast().factor();
    }

    /** 메뉴의 능력치들을 성장시키고, 능력치별 성장량을 돌려준다. */
    public Map<String, Double> train(GameState s, TrainingMenu menu, double baseGrowth, double conditionMultiplier,
                                     double bonus) {
        Map<String, Double> gains = new LinkedHashMap<>();
        for (String key : menu.stats()) {
            double before = s.stats.get(key);
            s.stats.add(key, growth(key, before, baseGrowth, menu.growthMultiplier(), conditionMultiplier, bonus));
            gains.put(key, s.stats.get(key) - before);
        }
        return gains;
    }
}
