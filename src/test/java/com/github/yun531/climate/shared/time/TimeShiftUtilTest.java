package com.github.yun531.climate.shared.time;

import com.github.yun531.climate.shared.time.TimeShiftUtil.ShiftResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimeShiftUtilTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 1, 22, 5, 0);

    @Nested
    @DisplayName("shiftHourly")
    class ShiftHourly {

        @Test
        @DisplayName("baseTime이 null이면 noShift 반환")
        void baseTimeNull_returnsNoShift() {
            ShiftResult result = TimeShiftUtil.shiftHourly(null, BASE, 2);

            assertThat(result.shiftHours()).isZero();
            assertThat(result.shiftedBaseTime()).isNull();
        }

        @Test
        @DisplayName("now가 null이면 noShift 반환")
        void nowNull_returnsNoShift() {
            ShiftResult result = TimeShiftUtil.shiftHourly(BASE, null, 2);

            assertThat(result.shiftHours()).isZero();
            assertThat(result.shiftedBaseTime()).isEqualTo(BASE);
        }

        @Test
        @DisplayName("maxShiftHours가 0이면 noShift 반환")
        void maxShiftZero_returnsNoShift() {
            LocalDateTime now = BASE.plusHours(3);
            ShiftResult result = TimeShiftUtil.shiftHourly(BASE, now, 0);

            assertThat(result.shiftHours()).isZero();
        }

        @Test
        @DisplayName("now가 base보다 과거이면 시프트 없음")
        void nowBeforeBase_noShift() {
            LocalDateTime now = BASE.minusHours(1);
            ShiftResult result = TimeShiftUtil.shiftHourly(BASE, now, 2);

            assertThat(result.shiftHours()).isZero();
        }

        @Test
        @DisplayName("차이가 maxShiftHours 이내이면 실제 차이만큼 시프트")
        void withinMax_shiftsActualDiff() {
            LocalDateTime now = BASE.plusHours(1).plusMinutes(30); // 1.5시간 후
            ShiftResult result = TimeShiftUtil.shiftHourly(BASE, now, 2);

            // truncatedTo(HOURS) 기준이므로 1시간 차이
            assertThat(result.shiftHours()).isEqualTo(1);
            assertThat(result.shiftedBaseTime()).isEqualTo(BASE.plusHours(1));
        }

        @Test
        @DisplayName("차이가 maxShiftHours 초과이면 max로 클램프")
        void exceedsMax_clampsToMax() {
            LocalDateTime now = BASE.plusHours(5);
            ShiftResult result = TimeShiftUtil.shiftHourly(BASE, now, 2);

            assertThat(result.shiftHours()).isEqualTo(2);
            assertThat(result.shiftedBaseTime()).isEqualTo(BASE.plusHours(2));
        }

        @Test
        @DisplayName("날짜 경계를 넘으면 dayShift 발생")
        void crossesDayBoundary_dayShiftPositive() {
            LocalDateTime base = LocalDateTime.of(2026, 1, 22, 23, 0);
            LocalDateTime now = base.plusHours(2);

            ShiftResult result = TimeShiftUtil.shiftHourly(base, now, 3);

            assertThat(result.shiftHours()).isEqualTo(2);
            assertThat(result.dayShift()).isEqualTo(1);
        }

        @Test
        @DisplayName("같은 날 시프트이면 dayShift는 0")
        void sameDayShift_dayShiftZero() {
            ShiftResult result = TimeShiftUtil.shiftHourly(BASE, BASE.plusHours(2), 3);

            assertThat(result.shiftHours()).isEqualTo(2);
            assertThat(result.dayShift()).isZero();
        }
    }
}
