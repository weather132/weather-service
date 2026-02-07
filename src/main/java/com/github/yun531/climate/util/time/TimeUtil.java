package com.github.yun531.climate.util.time;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class TimeUtil {

    private TimeUtil() { }

    public static LocalDateTime nowMinutes() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
    }
}