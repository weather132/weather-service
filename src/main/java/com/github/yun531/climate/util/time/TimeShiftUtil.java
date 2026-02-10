package com.github.yun531.climate.util.time;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * baseTime을 now 기준으로 재표현하기 위한 시간 시프트 계산 유틸.
 * 규칙:
 * - diffHours는 base/now를 시간 단위로 내림(truncatedTo(HOURS))해서 계산
 * - diffHours = clamp(hoursBetween(baseHour, nowHour), 0..maxShiftHours)
 * - shiftedBaseTime은 "시간 단위 윈도우 기준"이므로 baseHour + diffHours (시간 경계 정렬)
 * - dayShift는 날짜 경계 이동(일자 파트 보정용)
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

        // 시간 단위 시프트 결과는 시간 경계(정각)로 정렬
        LocalDateTime shiftedBaseTime = baseHour.plusHours(diffHours);

        int dayShift = (int) ChronoUnit.DAYS.between(baseTime.toLocalDate(), shiftedBaseTime.toLocalDate());
        return new Shift(diffHours, shiftedBaseTime, dayShift);
    }

    public record Shift(int diffHours, LocalDateTime shiftedBaseTime, int dayShift) {
        public static Shift zero(LocalDateTime baseTime) {
            return new Shift(0, baseTime, 0);
        }
    }
}