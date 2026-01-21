package com.github.yun531.climate.util.time;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * "기준 시각(baseTime)"을 "전송 시각(now)" 기준으로 재표현하기 위한 시간 시프트 계산 유틸.
 * - now/baseTime을 시간 단위로 내림(truncatedTo(HOURS))해서 비교
 * - shiftHours = clamp(hoursBetween(baseHour, nowHour), 0..maxShiftHours)
 * - shiftedBaseTime = baseTime + shiftHours
 * - dayShift = 날짜 경계 이동(일자 파트 보정용)
 */
public final class TimeShiftUtil {

    private TimeShiftUtil() {}

    public static Shift computeShift(LocalDateTime baseTime, LocalDateTime now, int maxShiftHours) {
        if (baseTime == null || now == null) {
            return new Shift(0, baseTime, 0);
        }

        LocalDateTime baseHour = baseTime.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime nowHour  = now.truncatedTo(ChronoUnit.HOURS);

        long raw = ChronoUnit.HOURS.between(baseHour, nowHour);
        if (raw <= 0) {
            return new Shift(0, baseTime, 0);
        }

        int shiftHours = (int) Math.min(raw, maxShiftHours);
        LocalDateTime shiftedBaseTime = baseTime.plusHours(shiftHours);

        int dayShift = (int) ChronoUnit.DAYS.between(baseTime.toLocalDate(), shiftedBaseTime.toLocalDate());
        if (dayShift < 0) dayShift = 0;

        return new Shift(shiftHours, shiftedBaseTime, dayShift);
    }

    public record Shift(
            int diffHours,
            LocalDateTime shiftedBaseTime,
            int dayShift
    ) {}
}