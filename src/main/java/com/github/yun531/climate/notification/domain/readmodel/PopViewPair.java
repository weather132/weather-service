package com.github.yun531.climate.notification.domain.readmodel;

import java.time.Duration;
import java.time.LocalDateTime;

public record PopViewPair(
        PopView current,
        PopView previous
) {
    public int reportTimeGapHoursRounded() {
        if (current == null || previous == null) return 0;
        LocalDateTime cur = current.reportTime();
        LocalDateTime prv = previous.reportTime();
        if (cur == null || prv == null) return 0;

        long minutes = Duration.between(prv, cur).toMinutes();
        return (int) Math.round(minutes / 60.0);
    }
}