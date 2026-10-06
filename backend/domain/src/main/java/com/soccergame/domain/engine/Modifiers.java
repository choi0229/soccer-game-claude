package com.soccergame.domain.engine;

import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.config.Rules;
import com.soccergame.domain.config.Rules.BondDef;
import com.soccergame.domain.config.Rules.TraitDef;
import com.soccergame.domain.model.Axis;
import com.soccergame.domain.model.TrainingSlot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 특성 단계와 인연 단계, 그리고 그 효과(훈련 보너스, 학업 감소율, 밤 회복). */
public final class Modifiers {
    private final GameConfig config;
    private final Rules rules;

    public Modifiers(GameConfig config) {
        this.config = config;
        this.rules = config.rules();
    }

    /** 보너스 한 항목. label 예: "승부사", "감독 인연" */
    public record Part(String label, double value) {
    }

    /** 훈련 1회에 붙는 보너스. applied = min(raw, 상한) */
    public record TrainingBonus(List<Part> parts, double raw, double applied, boolean capped) {
        public static final TrainingBonus NONE = new TrainingBonus(List.of(), 0, 0, false);
    }

    public int traitTier(GameState s, String traitKey) {
        return tier(s.traitScores.getOrDefault(traitKey, 0), rules.traits().tiers());
    }

    /** 잠긴 축은 -1 */
    public int bondTier(GameState s, Axis axis) {
        Double affinity = s.affinity.get(axis);
        if (affinity == null) {
            return -1;
        }
        int t = 0;
        for (double threshold : rules.bonds().tiers()) {
            if (affinity >= threshold) {
                t++;
            }
        }
        return t;
    }

    private static int tier(int score, List<Integer> tiers) {
        int t = 0;
        for (int threshold : tiers) {
            if (score >= threshold) {
                t++;
            }
        }
        return t;
    }

    public TrainingBonus training(GameState s, TrainingSlot slot, String menuId) {
        List<Part> parts = new ArrayList<>();
        for (TraitDef trait : rules.traits().list()) {
            int t = traitTier(s, trait.key());
            if (t > 0 && trait.trainingBonus() != null && trait.trainingBonus().appliesTo(slot, menuId)) {
                parts.add(new Part(trait.name(), trait.trainingBonus().values().get(t - 1)));
            }
        }
        for (BondDef bond : rules.bonds().axes()) {
            int t = bondTier(s, bond.axis());
            if (t > 0 && bond.trainingBonus() != null && bond.trainingBonus().appliesTo(slot, menuId)) {
                parts.add(new Part(bond.axis().label() + " 인연", bond.trainingBonus().values().get(t - 1)));
            }
        }
        double raw = parts.stream().mapToDouble(Part::value).sum();
        double cap = rules.training().bonusCap();
        return new TrainingBonus(parts, raw, Math.min(raw, cap), raw > cap);
    }

    /** 학기 중 주간 학업 감소율. 모범생 단계가 있으면 그 값으로 바뀐다 */
    public double semesterWeeklyRate(GameState s) {
        double rate = rules.academics().semesterWeeklyRate();
        for (TraitDef trait : rules.traits().list()) {
            int t = traitTier(s, trait.key());
            if (t > 0 && trait.semesterWeeklyRate() != null) {
                rate = trait.semesterWeeklyRate().get(t - 1);
            }
        }
        return rate;
    }

    /** 인연으로 늘어나는 밤 체력 회복량 */
    public List<Part> nightRecoveryBonus(GameState s) {
        List<Part> parts = new ArrayList<>();
        for (BondDef bond : rules.bonds().axes()) {
            int t = bondTier(s, bond.axis());
            if (t > 0 && bond.nightRecovery() != null) {
                parts.add(new Part(bond.axis().label() + " 인연", bond.nightRecovery().get(t - 1)));
            }
        }
        return parts;
    }

    /** 단계 변화 알림용 스냅샷: 이름 → 단계 (잠긴 축은 -1) */
    public Map<String, Integer> snapshot(GameState s) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (TraitDef trait : rules.traits().list()) {
            map.put("trait:" + trait.key(), traitTier(s, trait.key()));
        }
        for (BondDef bond : rules.bonds().axes()) {
            map.put("bond:" + bond.axis().name(), bondTier(s, bond.axis()));
        }
        return map;
    }

    /** 두 스냅샷의 차이를 사람이 읽을 알림 문장으로 */
    public List<String> changes(Map<String, Integer> before, Map<String, Integer> after) {
        List<String> notes = new ArrayList<>();
        for (TraitDef trait : rules.traits().list()) {
            int a = before.get("trait:" + trait.key());
            int b = after.get("trait:" + trait.key());
            if (a != b) {
                notes.add("특성 '" + trait.name() + "' " + a + "단계 → " + b + "단계: " + traitEffect(trait, b));
            }
        }
        for (BondDef bond : rules.bonds().axes()) {
            int a = before.get("bond:" + bond.axis().name());
            int b = after.get("bond:" + bond.axis().name());
            if (a == b) {
                continue;
            }
            if (a < 0) {
                notes.add(bond.axis().label() + " 축이 열렸다 (인연 " + b + "단계)");
            } else {
                notes.add(bond.axis().label() + " 인연 " + a + "단계 → " + b + "단계: " + bondEffect(bond, b));
            }
        }
        return notes;
    }

    public String traitEffect(TraitDef trait, int tier) {
        if (tier <= 0) {
            return "효과 없음";
        }
        if (trait.trainingBonus() != null) {
            return trait.description() + " +" + pct(trait.trainingBonus().values().get(tier - 1));
        }
        if (trait.semesterWeeklyRate() != null) {
            return trait.description() + " " + pct(-rules.academics().semesterWeeklyRate()) + " → "
                    + pct(-trait.semesterWeeklyRate().get(tier - 1));
        }
        return trait.description();
    }

    public String bondEffect(BondDef bond, int tier) {
        if (tier <= 0) {
            return "효과 없음";
        }
        if (bond.trainingBonus() != null) {
            return bond.description() + " +" + pct(bond.trainingBonus().values().get(tier - 1));
        }
        if (bond.nightRecovery() != null) {
            return bond.description() + " +" + Resources.plain(bond.nightRecovery().get(tier - 1));
        }
        return bond.description();
    }

    public static String pct(double ratio) {
        return Resources.plain(Math.round(ratio * 1000) / 10.0) + "%";
    }

    public GameConfig config() {
        return config;
    }
}
