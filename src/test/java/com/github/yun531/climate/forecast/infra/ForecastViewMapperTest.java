package com.github.yun531.climate.forecast.infra;

import com.github.yun531.climate.forecast.domain.readmodel.ForecastDailyView;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastHourlyView;
import com.github.yun531.climate.snapshot.domain.readmodel.DailyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.HourlyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ForecastViewMapperTest {

    private final ForecastViewMapper mapper = new ForecastViewMapper();

    private static final LocalDateTime ANNOUNCE_TIME = LocalDateTime.of(2026, 1, 22, 5, 0);

    @Test
    @DisplayName("toHourlyView — 정상 변환 + validAt 정렬")
    void toHourlyView_sortsAndMaps() {
        // 역순으로 제공
        WeatherSnapshot snap = new WeatherSnapshot("R1", ANNOUNCE_TIME, List.of(
                new HourlyPoint(ANNOUNCE_TIME.plusHours(3), 8, 60),
                new HourlyPoint(ANNOUNCE_TIME.plusHours(1), 2, 20),
                new HourlyPoint(ANNOUNCE_TIME.plusHours(2), 5, 40)
        ), List.of());

        ForecastHourlyView view = mapper.toHourlyView(snap);

        assertThat(view.regionId()).isEqualTo("R1");
        assertThat(view.reportTime()).isEqualTo(ANNOUNCE_TIME);
        assertThat(view.hourlyPoints()).hasSize(3);
        // validAt 정렬 확인
        assertThat(view.hourlyPoints().get(0).validAt()).isEqualTo(ANNOUNCE_TIME.plusHours(1));
        assertThat(view.hourlyPoints().get(2).validAt()).isEqualTo(ANNOUNCE_TIME.plusHours(3));
    }

    @Test
    @DisplayName("toDailyView — dayOffset 정렬 + 필드 매핑")
    void toDailyView_sortsAndMaps() {
        WeatherSnapshot snap = new WeatherSnapshot("R1", ANNOUNCE_TIME, List.of(),
                List.of(
                        new DailyPoint(2, 0, 10, 30, 50),
                        new DailyPoint(0, -5, 5, 10, 20),
                        new DailyPoint(1, -3, 7, 20, 40)
                ));

        ForecastDailyView view = mapper.toDailyView(snap);

        assertThat(view.dailyPoints()).hasSize(3);
        assertThat(view.dailyPoints().get(0).dayOffset()).isZero();
        assertThat(view.dailyPoints().get(0).minTemp()).isEqualTo(-5);
    }

    @Test
    @DisplayName("null 스냅샷 -> null 반환")
    void nullSnapshot_returnsNull() {
        assertThat(mapper.toHourlyView(null)).isNull();
        assertThat(mapper.toDailyView(null)).isNull();
    }

    @Test
    @DisplayName("빈 hourly -> 빈 포인트 리스트")
    void emptyHourly_emptyPoints() {
        WeatherSnapshot snap = new WeatherSnapshot("R1", ANNOUNCE_TIME, List.of(), List.of());
        ForecastHourlyView view = mapper.toHourlyView(snap);
        assertThat(view.hourlyPoints()).isEmpty();
    }
}
