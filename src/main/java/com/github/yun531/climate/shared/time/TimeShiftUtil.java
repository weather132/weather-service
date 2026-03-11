package com.github.yun531.climate.shared.time;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * baseTime을 now 기준으로 최대 maxShiftHours 만큼 시(정각) 단위로 시프트하는 유틸리티.
 * - shiftHours      : baseHour~nowHour 차이를 0..maxShiftHours로 클램프
 * - shiftedBaseTime : baseHour + shiftHours (정각 정렬)
 * - dayShift        : 시프트로 인한 날짜 경계 이동 횟수
 */
public final class TimeShiftUtil {

    private TimeShiftUtil() {}

    /** baseTime을 now 기준으로 최대 maxShiftHours 만큼 시(정각) 단위로 당긴 결과를 반환 */
    public static ShiftResult shiftHourly(LocalDateTime baseTime, LocalDateTime now, int maxShiftHours) {
        if (baseTime == null) return ShiftResult.noShift(null);
        if (now == null || maxShiftHours <= 0) return ShiftResult.noShift(baseTime);

        LocalDateTime baseHour = truncateToHour(baseTime);
        LocalDateTime nowHour  = truncateToHour(now);

        int shiftHours = computeShiftHours(baseHour, nowHour, maxShiftHours);
        if (shiftHours == 0) return ShiftResult.noShift(baseTime);

        LocalDateTime shiftedBaseTime = baseHour.plusHours(shiftHours);
        int dayShift = computeDayShift(baseTime.toLocalDate(), shiftedBaseTime.toLocalDate());

        return new ShiftResult(shiftHours, shiftedBaseTime, dayShift);
    }


    private static LocalDateTime truncateToHour(LocalDateTime t) {
        return t.truncatedTo(ChronoUnit.HOURS);
    }

    private static int computeShiftHours(LocalDateTime baseHour, LocalDateTime nowHour, int maxShiftHours) {
        long rawHours = ChronoUnit.HOURS.between(baseHour, nowHour);
        if (rawHours <= 0) return 0;
        return (int) Math.min(rawHours, (long) maxShiftHours);
    }

    private static int computeDayShift(LocalDate baseDate, LocalDate shiftedDate) {
        return (int) ChronoUnit.DAYS.between(baseDate, shiftedDate);
    }

    public record ShiftResult(int shiftHours, LocalDateTime shiftedBaseTime, int dayShift) {

        public static ShiftResult noShift(LocalDateTime baseTime) {
            // baseTime이 정각이 아니어도 "원본 기준"을 그대로 두어 호출부 판단을 쉽게 한다.
            return new ShiftResult(0, baseTime, 0);
        }
    }
}