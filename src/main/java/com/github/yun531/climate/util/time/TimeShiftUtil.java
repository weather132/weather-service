package com.github.yun531.climate.util.time;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * baseTime을 now 기준으로 재표현하기 위한 시간 시프트 계산 유틸.
 * - baseTime/now 를 시간 단위로 내림(truncatedTo(HOURS))해서 diffHours 계산
 * - diffHours = clamp(hoursBetween(baseHour, nowHour), 0..maxShiftHours)
 * - shiftedBaseTime = baseTime + diffHours
 * - dayShift = 날짜 경계 이동(일자 파트 보정용)
 */
public final class TimeShiftUtil {

    private TimeShiftUtil() {}

    public static Shift computeShift(LocalDateTime baseTime, LocalDateTime now, int maxShiftHours) {
        if (baseTime == null || now == null) return Shift.zero(baseTime);
        if (maxShiftHours <= 0) return Shift.zero(baseTime);

        LocalDateTime baseHour = baseTime.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime nowHour  = now.truncatedTo(ChronoUnit.HOURS);

        long raw = ChronoUnit.HOURS.between(baseHour, nowHour);
        if (raw <= 0) return Shift.zero(baseTime);

        int diffHours = (int) Math.min(raw, (long) maxShiftHours);
        LocalDateTime shiftedBaseTime = baseTime.plusHours(diffHours);

        int dayShift = (int) ChronoUnit.DAYS.between(baseTime.toLocalDate(), shiftedBaseTime.toLocalDate());
        return new Shift(diffHours, shiftedBaseTime, dayShift);
    }

    public record Shift(
            int diffHours,
            LocalDateTime shiftedBaseTime,
            int dayShift
    ) {
        public static Shift zero(LocalDateTime baseTime) {
            return new Shift(0, baseTime, 0);
        }
    }
}