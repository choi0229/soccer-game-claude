package com.soccergame.domain.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 능력치 키 → 값. 값은 항상 [min, max] 로 제한된다. */
public final class Stats {
    private final Map<String, Double> values = new LinkedHashMap<>();
    private final double min;
    private final double max;

    public Stats(double min, double max) {
        this.min = min;
        this.max = max;
    }

    public double get(String key) {
        Double v = values.get(key);
        if (v == null) {
            throw new IllegalArgumentException("알 수 없는 능력치: " + key);
        }
        return v;
    }

    public void set(String key, double value) {
        values.put(key, Math.max(min, Math.min(max, value)));
    }

    public void add(String key, double delta) {
        set(key, get(key) + delta);
    }

    public double average(Collection<String> keys) {
        return keys.stream().mapToDouble(this::get).average().orElse(0);
    }

    public Map<String, Double> asMap() {
        return Collections.unmodifiableMap(values);
    }

    public Stats copy() {
        Stats copy = new Stats(min, max);
        copy.values.putAll(values);
        return copy;
    }
}
