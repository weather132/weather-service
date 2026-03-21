package com.github.yun531.climate.forecast.domain.adjust;

import com.github.yun531.climate.forecast.domain.readmodel.ForecastHourlyPoint;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastHourlyView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForecastWindowAdjusterTest {

    private static final LocalDateTime ANNOUNCE_TIME = LocalDateTime.of(2026, 1, 22, 5, 0);

    private final ForecastWindowAdjuster adjuster = new ForecastWindowAdjuster(2, 24);

    //  --- 생성자 검증 ---

    @Nested
    @DisplayName("생성자 검증")
    class Constructor {

        @Test
        void negativeMaxShift_throws() {
            assertThatThrownBy(() -> new ForecastWindowAdjuster(-1, 24))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void zeroWindowSize_throws() {
            assertThatThrownBy(() -> new ForecastWindowAdjuster(2, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    //  --- null 가드 ---

    @Nested
    @DisplayName("null 가드")
    class NullGuards {

        @Test
        @DisplayName("base가 null 이면 null 반환")
        void nullBase_returnsNull() {
            assertThat(adjuster.adjust(null, ANNOUNCE_TIME)).isNull();
        }

        @Test
        @DisplayName("now가 null → 시프트 없이 정렬된 포인트 반환")
        void nullNow_noShift() {
            ForecastHourlyView base = buildView(ANNOUNCE_TIME, 5);

            ForecastHourlyView result = adjuster.adjust(base, null);

            assertThat(result.reportTime()).isEqualTo(ANNOUNCE_TIME);
            assertThat(result.hourlyPoints()).hasSize(5);
        }

        @Test
        @DisplayName("announceTime이 null 이면 시프트 없이 정렬만")
        void nullAnnounceTime_noShift() {
            ForecastHourlyView base = new ForecastHourlyView("R1", null, List.of(
                    new ForecastHourlyPoint(ANNOUNCE_TIME.plusHours(1), 10, 20)
            ));

            ForecastHourlyView result = adjuster.adjust(base, ANNOUNCE_TIME);

            assertThat(result.reportTime()).isNull();
            assertThat(result.hourlyPoints()).hasSize(1);
        }
    }

    //  --- 시프트 및 클램프 ---

    @Nested
    @DisplayName("시프트 및 클램프")
    class ShiftAndClip {

        @Test
        @DisplayName("now == announceTime → 시프트 없이 발표시각 이후 포인트만 필터링")
        void noShift_filtersAfterAnnounceTime() {
            ForecastHourlyView base = buildView(ANNOUNCE_TIME, 26);

            ForecastHourlyView result = adjuster.adjust(base, ANNOUNCE_TIME);

            // 발표시각(05:00) 이후인 06:00~부터만 남아야 함
            assertThat(result.hourlyPoints()).isNotEmpty();
            result.hourlyPoints().forEach(p ->
                    assertThat(p.validAt()).isAfter(ANNOUNCE_TIME));
        }

        @Test
        @DisplayName("now = announceTime + 1h → 1시간 시프트, 06:00 이후 포인트만")
        void oneHourShift_adjustsAnnounceTime() {
            LocalDateTime now = ANNOUNCE_TIME.plusHours(1);
            ForecastHourlyView base = buildView(ANNOUNCE_TIME, 26);

            ForecastHourlyView result = adjuster.adjust(base, now);

            assertThat(result.reportTime()).isEqualTo(ANNOUNCE_TIME.plusHours(1));
            result.hourlyPoints().forEach(p ->
                    assertThat(p.validAt()).isAfter(ANNOUNCE_TIME.plusHours(1)));
        }

        @Test
        @DisplayName("now = announceTime + 5h → maxShift(2)로 클램프")
        void exceedsMax_clampsShift() {
            LocalDateTime now = ANNOUNCE_TIME.plusHours(5);
            ForecastHourlyView base = buildView(ANNOUNCE_TIME, 26);

            ForecastHourlyView result = adjuster.adjust(base, now);

            assertThat(result.reportTime()).isEqualTo(ANNOUNCE_TIME.plusHours(2));
        }

        @Test
        @DisplayName("windowSize=3 → 최대 3개 포인트만 반환")
        void windowSizeLimit() {
            var smallAdjuster = new ForecastWindowAdjuster(2, 3);
            ForecastHourlyView base = buildView(ANNOUNCE_TIME, 26);

            ForecastHourlyView result = smallAdjuster.adjust(base, ANNOUNCE_TIME);

            assertThat(result.hourlyPoints()).hasSizeLessThanOrEqualTo(3);
        }
    }

    //  --- 윈도우 필터링 ---

    @Nested
    @DisplayName("윈도우 필터링")
    class WindowFiltering {

        @Test
        @DisplayName("hourlyPoints가 빈 리스트 → 시프트 없이 빈 리스트 반환")
        void emptyPoints_returnsEmpty() {
            ForecastHourlyView base = new ForecastHourlyView("R1", ANNOUNCE_TIME, List.of());

            ForecastHourlyView result = adjuster.adjust(base, ANNOUNCE_TIME);

            assertThat(result.hourlyPoints()).isEmpty();
            assertThat(result.reportTime()).isEqualTo(ANNOUNCE_TIME);
        }

        @Test
        @DisplayName("validAt이 null인 포인트 → filterByWindow 에서 제외")
        void nullValidAtPoint_filtered() {
            ForecastHourlyView base = new ForecastHourlyView("R1", ANNOUNCE_TIME, List.of(
                    new ForecastHourlyPoint(null, 10, 20),
                    new ForecastHourlyPoint(ANNOUNCE_TIME.plusHours(1), 12, 30)
            ));

            ForecastHourlyView result = adjuster.adjust(base, ANNOUNCE_TIME);

            assertThat(result.hourlyPoints()).hasSize(1);
            assertThat(result.hourlyPoints().get(0).validAt()).isEqualTo(ANNOUNCE_TIME.plusHours(1));
        }

        @Test
        @DisplayName("윈도우 내 포인트가 windowSize 보다 적으면 전부 반환")
        void fewerPointsThanWindowSize_allReturned() {
            ForecastHourlyView base = buildView(ANNOUNCE_TIME, 5);

            ForecastHourlyView result = adjuster.adjust(base, ANNOUNCE_TIME);

            assertThat(result.hourlyPoints()).hasSize(5);
        }

        @Nested
        @DisplayName("경계값 미포함 필터링")
        class StrictlyAfter {

            @Test
            @DisplayName("effectiveTime == shiftedTime → 제외")
            void validAtEqualsShiftedTime_excluded() {
                ForecastHourlyView base = new ForecastHourlyView("R1", ANNOUNCE_TIME, List.of(
                        new ForecastHourlyPoint(ANNOUNCE_TIME, 10, 20),
                        new ForecastHourlyPoint(ANNOUNCE_TIME.plusHours(1), 12, 30)
                ));

                ForecastHourlyView result = adjuster.adjust(base, ANNOUNCE_TIME);

                assertThat(result.hourlyPoints()).hasSize(1);
                assertThat(result.hourlyPoints().get(0).validAt()).isEqualTo(ANNOUNCE_TIME.plusHours(1));
            }

            @Test
            @DisplayName("validAt이 shiftedTime 보다 1분 뒤 → 포함")
            void validAtOneMinuteAfter_included() {
                ForecastHourlyView base = new ForecastHourlyView("R1", ANNOUNCE_TIME, List.of(
                        new ForecastHourlyPoint(ANNOUNCE_TIME.plusMinutes(1), 10, 20)
                ));

                ForecastHourlyView result = adjuster.adjust(base, ANNOUNCE_TIME);

                assertThat(result.hourlyPoints()).hasSize(1);
            }
        }
    }

    //  --- 정렬 검증 ---

    @Nested
    @DisplayName("정렬 검증")
    class Sorting {

        @Test
        @DisplayName("역순 입력 → effectiveTime 기준 오름차순 정렬")
        void reverseOrder_sortedCorrectly() {
            ForecastHourlyView base = new ForecastHourlyView("R1", ANNOUNCE_TIME, List.of(
                    new ForecastHourlyPoint(ANNOUNCE_TIME.plusHours(3), 30, 50),
                    new ForecastHourlyPoint(ANNOUNCE_TIME.plusHours(1), 10, 20),
                    new ForecastHourlyPoint(ANNOUNCE_TIME.plusHours(2), 20, 40)
            ));

            ForecastHourlyView result = adjuster.adjust(base, ANNOUNCE_TIME);

            assertThat(result.hourlyPoints()).hasSize(3);
            assertThat(result.hourlyPoints().get(0).validAt()).isEqualTo(ANNOUNCE_TIME.plusHours(1));
            assertThat(result.hourlyPoints().get(1).validAt()).isEqualTo(ANNOUNCE_TIME.plusHours(2));
            assertThat(result.hourlyPoints().get(2).validAt()).isEqualTo(ANNOUNCE_TIME.plusHours(3));
        }
    }

    //  --- 헬퍼 ---

    private ForecastHourlyView buildView(LocalDateTime announceTime, int count) {
        List<ForecastHourlyPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new ForecastHourlyPoint(
                    announceTime.plusHours(i + 1), i * 2, i * 3));
        }
        return new ForecastHourlyView("R1", announceTime, points);
    }
}