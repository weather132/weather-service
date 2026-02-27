package com.github.yun531.climate.shared.time;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class TimeUtil {

    private TimeUtil() { }

    public static LocalDateTime nowMinutes() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    }

    public static LocalDateTime truncateToMinutes(LocalDateTime t) {
        return t == null ? null : t.truncatedTo(ChronoUnit.MINUTES);
    }
}