package com.soccergame.domain.engine;

import com.soccergame.domain.config.Events.Effects;
import com.soccergame.domain.config.GameConfig;
import com.soccergame.domain.config.Rules;
import com.soccergame.domain.model.Axis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 체력·컨디션·관계도 등 자원 증감. 모든 값은 여기서 범위가 제한된다. */
public final class Resources {
    private final GameConfig config;
    private final Rules rules;

    public Resources(GameConfig config) {
        this.config = config;
        this.rules = config.rules();
    }

    /** 체력 최대치 = 기본 + (기초체력 - 기준) / 나눗수 */
    public double maxStamina(GameState s) {
        Rules.StaminaRules r = rules.stamina();
        return r.maxBase() + (s.stats.get(GameConfig.FITNESS) - r.fitnessPivot()) / r.fitnessDivisor();
    }

    public void stamina(GameState s, double delta) {
        s.stamina = clamp(s.stamina + delta, 0, maxStamina(s));
    }

    public void condition(GameState s, int delta) {
        s.condition = (int) clamp(s.condition + delta, 0, rules.condition().levels().size() - 1);
    }

    public Rules.ConditionLevel conditionLevel(GameState s) {
        return rules.condition().levels().get(s.condition);
    }

    public void affinity(GameState s, Axis axis, double delta) {
        Rules.RelationshipRules r = rules.relationships();
        s.affinity.put(axis, clamp(s.affinity.get(axis) + delta, r.min(), r.max()));
    }

    public void academics(GameState s, double delta) {
        Rules.AcademicsRules r = rules.academics();
        s.academics = clamp(s.academics + delta, r.min(), r.max());
    }

    public void money(GameState s, long delta) {
        s.money = Math.max(0, s.money + delta);
    }

    public void reputation(GameState s, double delta) {
        s.reputation = Math.max(0, s.reputation + delta);
    }

    /** 학교생활 배율 = base + 학교생활 관계도 / divisor */
    public double schoolLifeMultiplier(GameState s) {
        Rules.SchoolLifeMultiplier m = rules.daily().schoolLifeMultiplier();
        return m.base() + s.affinity.get(Axis.SCHOOL) / m.divisor();
    }

    /** 이벤트 효과를 적용하고, 적용한 내용을 사람이 읽을 문장으로 돌려준다. */
    public String apply(GameState s, Effects effects) {
        if (effects == null) {
            return "변화 없음";
        }
        List<String> parts = new ArrayList<>();
        if (effects.stat() != null) {
            for (Map.Entry<String, Double> e : effects.stat().entrySet()) {
                s.stats.add(e.getKey(), e.getValue());
                parts.add(config.stat(e.getKey()).name() + " " + signed(e.getValue()));
            }
        }
        if (effects.stamina() != null) {
            stamina(s, effects.stamina());
            parts.add("체력 " + signed(effects.stamina()));
        }
        if (effects.condition() != null) {
            condition(s, effects.condition());
            parts.add("컨디션 " + signed(effects.condition()));
        }
        if (effects.money() != null) {
            money(s, effects.money());
            parts.add("돈 " + signed(effects.money()) + "원");
        }
        if (effects.academics() != null) {
            academics(s, effects.academics());
            parts.add("학업 성취 " + signed(effects.academics()));
        }
        affinityEffect(s, Axis.COACH, effects.coachAffinity(), parts);
        affinityEffect(s, Axis.TEAMMATE, effects.teammateAffinity(), parts);
        affinityEffect(s, Axis.FAMILY, effects.familyAffinity(), parts);
        affinityEffect(s, Axis.SCHOOL, effects.schoolAffinity(), parts);
        if (effects.reputation() != null) {
            reputation(s, effects.reputation());
            parts.add("평판 " + signed(effects.reputation()));
        }
        return parts.isEmpty() ? "변화 없음" : String.join(", ", parts);
    }

    private void affinityEffect(GameState s, Axis axis, Double delta, List<String> parts) {
        if (delta != null) {
            affinity(s, axis, delta);
            parts.add(axis.label() + " 관계도 " + signed(delta));
        }
    }

    public static String signed(double v) {
        return v >= 0 ? "+" + plain(v) : plain(v);
    }

    /** 부호 없이 짧게: 30, 1.5, 0.04 */
    public static String plain(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.2f", v).replaceAll("0+$", "");
    }

    public static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}
