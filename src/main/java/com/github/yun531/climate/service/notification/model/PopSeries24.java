package com.github.yun531.climate.service.notification.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PopSeries24 {
    private final List<Integer> values; // size 26

    /** 1~26시간 후 POP 조회 */
    public int get(int offsetHour) {
        return values.get(offsetHour - 1);
    }

    /** 내부 index 기반 접근 */
    public int getByIndex(int index0to23) {
        return values.get(index0to23);
    }

    public int size() { return values.size(); }

    public int max() {
        int max = 0;
        for (Integer v : values) {
            if (v != null && v > max) {
                max = v;
            }
        }
        return max;
    }
}
