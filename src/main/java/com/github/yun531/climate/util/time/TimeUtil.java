package com.github.yun531.climate.util.time;

import java.time.LocalDateTime;

public final class TimeUtil {

    private TimeUtil() { }

    public static LocalDateTime nowMinutes() {
        return LocalDateTime.now()
                .withSecond(0)
                .withNano(0);
    }
}
