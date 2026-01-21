package com.github.yun531.climate.service.notification.model;

import java.util.List;

public record PopDailySeries7(List<DailyPop> days) {

    public PopDailySeries7(List<DailyPop> days) {
        if (days.size() != 7) {                 /* @param days size 7 */
            throw new IllegalArgumentException("days size must be 7");
        }
        this.days = List.copyOf(days); // 방어적 복사 + 불변 리스트
    }

    public DailyPop get(int dayOffset) {
        return days.get(dayOffset);
    }

    public record DailyPop(int am, int pm) {
    }
}
