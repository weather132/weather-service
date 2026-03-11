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

    @Nested
    @DisplayName("adjust")
    class Adjust {

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

        @Test
        @DisplayName("base가 null 이면 null 반환")
        void nullBase_returnsNull() {
            assertThat(adjuster.adjust(null, ANNOUNCE_TIME)).isNull();
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

    // -- 테스트 헬퍼 --

    private ForecastHourlyView buildView(LocalDateTime announceTime, int count) {
        List<ForecastHourlyPoint> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(new ForecastHourlyPoint(
                    announceTime.plusHours(i + 1), i * 2, i * 3));
        }
        return new ForecastHourlyView("R1", announceTime, points);
    }
}