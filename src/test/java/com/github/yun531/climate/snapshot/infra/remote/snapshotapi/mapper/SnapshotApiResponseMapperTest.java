package com.github.yun531.climate.snapshot.infra.remote.snapshotapi.mapper;

import com.github.yun531.climate.snapshot.domain.readmodel.DailyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.HourlyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import com.github.yun531.climate.snapshot.infra.remote.snapshotapi.dto.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotApiResponseMapperTest {

    private final SnapshotApiResponseMapper mapper = new SnapshotApiResponseMapper();

    private static final LocalDateTime ANNOUNCE_TIME = LocalDateTime.of(2026, 1, 22, 5, 0);
    private static final LocalDate BASE_DATE         = ANNOUNCE_TIME.toLocalDate();

    @Test
    @DisplayName("hourly + daily 정상 조합 → WeatherSnapshot 생성")
    void normalResponse_producesSnapshot() {
        HourlyForecastResponse hourly = new HourlyForecastResponse(
                ANNOUNCE_TIME, 60, 127,
                List.of(
                        new HourlyForecastItem(ANNOUNCE_TIME.plusHours(1), 40, 1),
                        new HourlyForecastItem(ANNOUNCE_TIME.plusHours(2), 50, 2),
                        new HourlyForecastItem(ANNOUNCE_TIME.plusHours(3), 60, 3)
                )
        );

        DailyForecastResponse daily = new DailyForecastResponse(
                "11B10101",
                List.of(
                        new DailyForecastItem(ANNOUNCE_TIME, BASE_DATE.atTime(9, 0), -5, 30),
                        new DailyForecastItem(ANNOUNCE_TIME, BASE_DATE.atTime(21, 0), -2, 60)
                )
        );

        WeatherSnapshot snap = mapper.toSnapshot("11B10101", hourly, daily, BASE_DATE);

        assertThat(snap.regionId()).isEqualTo("11B10101");
        assertThat(snap.reportTime()).isEqualTo(ANNOUNCE_TIME);
        assertThat(snap.hourly()).hasSize(3);
        assertThat(snap.daily()).hasSize(7);

        // hourly는 effectiveTime 정렬
        HourlyPoint first = snap.hourly().get(0);
        assertThat(first.validAt()).isEqualTo(ANNOUNCE_TIME.plusHours(1));
        assertThat(first.pop()).isEqualTo(40);
        assertThat(first.temp()).isEqualTo(1);
    }

    @Test
    @DisplayName("hourly 26개 초과 → 최대 26개로 제한")
    void hourlyExceedsMax_truncatedTo26() {
        List<HourlyForecastItem> points = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            points.add(new HourlyForecastItem(ANNOUNCE_TIME.plusHours(i + 1), 10, i));
        }

        HourlyForecastResponse hourly = new HourlyForecastResponse(ANNOUNCE_TIME, 60, 127, points);
        DailyForecastResponse daily = new DailyForecastResponse("R1", List.of());

        WeatherSnapshot snap = mapper.toSnapshot("R1", hourly, daily, BASE_DATE);
        assertThat(snap.hourly()).hasSize(26);
    }

    @Test
    @DisplayName("hourly null 응답 → 빈 리스트")
    void hourlyNull_emptyList() {
        HourlyForecastResponse hourly = new HourlyForecastResponse(ANNOUNCE_TIME, 60, 127, null);
        DailyForecastResponse daily = new DailyForecastResponse("R1", List.of());

        WeatherSnapshot snap = mapper.toSnapshot("R1", hourly, daily, BASE_DATE);
        assertThat(snap.hourly()).isEmpty();
    }

    @Test
    @DisplayName("daily baseDate null → 7개 빈 DailyPoint")
    void dailyBaseDateNull_emptyDailyPoints() {
        HourlyForecastResponse hourly = new HourlyForecastResponse(ANNOUNCE_TIME, 60, 127, List.of());
        DailyForecastResponse daily = new DailyForecastResponse("R1", List.of());

        WeatherSnapshot snap = mapper.toSnapshot("R1", hourly, daily, null);
        assertThat(snap.daily()).hasSize(7);
        assertThat(snap.daily().get(0).minTemp()).isNull();
    }

    @Test
    @DisplayName("daily AM/PM 분리 — hour<12 → amPop, hour>=12 → pmPop")
    void dailyAmPmSplit() {
        DailyForecastResponse daily = new DailyForecastResponse("R1", List.of(
                new DailyForecastItem(ANNOUNCE_TIME, BASE_DATE.atTime(9, 0), -5, 30),  // AM
                new DailyForecastItem(ANNOUNCE_TIME, BASE_DATE.atTime(21, 0), -2, 70)  // PM
        ));
        HourlyForecastResponse hourly = new HourlyForecastResponse(ANNOUNCE_TIME, 60, 127, List.of());

        WeatherSnapshot snap = mapper.toSnapshot("R1", hourly, daily, BASE_DATE);
        DailyPoint day0 = snap.daily().get(0);

        assertThat(day0.amPop()).isEqualTo(30);
        assertThat(day0.pmPop()).isEqualTo(70);
        assertThat(day0.minTemp()).isEqualTo(-5);
        assertThat(day0.maxTemp()).isEqualTo(-2);
    }
}
