package com.github.yun531.climate.util.time;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * "스냅샷(혹은 기준 시각) 결과"를 "전송 시각(now)" 기준으로 재표현하기 위한 시간 시프트 계산 유틸.
 * - diffHours = floor((now - baseTime)/1h)
 * - diffHours는 정책에 따라 maxShiftHours로 클램프
 * - dayShift는 날짜 경계 이동(일자 파트 보정용)
 */
public final class OffsetShiftUtil {

    private OffsetShiftUtil() {}

    public static OffsetShift compute(LocalDateTime baseTime, LocalDateTime now, int maxShiftHours) {
        if (baseTime == null || now == null) {
            return new OffsetShift(0, baseTime, 0);
        }

        LocalDateTime bt = baseTime.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime nw = now.truncatedTo(ChronoUnit.HOURS);

        long diffHoursRaw = ChronoUnit.HOURS.between(bt, nw);
        if (diffHoursRaw <= 0) {
            return new OffsetShift(0, baseTime, 0);
        }

        int diffHours = (int) Math.min(diffHoursRaw, (long) maxShiftHours);
        LocalDateTime shiftedBaseTime = baseTime.plusHours(diffHours);

        int dayShift = (int) ChronoUnit.DAYS.between(baseTime.toLocalDate(), shiftedBaseTime.toLocalDate());
        if (dayShift < 0) dayShift = 0;

        return new OffsetShift(diffHours, shiftedBaseTime, dayShift);
    }

    public record OffsetShift(
            int diffHours,
            LocalDateTime shiftedBaseTime,
            int dayShift
    ) {}
}