package com.github.yun531.climate.forecast.infra;

import com.github.yun531.climate.forecast.domain.readmodel.ForecastDailyView;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastHourlyView;
import com.github.yun531.climate.snapshot.domain.reader.SnapshotReader;
import com.github.yun531.climate.snapshot.domain.readmodel.HourlyPoint;
import com.github.yun531.climate.snapshot.domain.readmodel.WeatherSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SnapshotForecastViewReaderTest {

    @Mock SnapshotReader snapshotReader;
    @Spy  ForecastViewMapper mapper = new ForecastViewMapper();   // SnapshotForecastViewReader의 의존성
    @InjectMocks SnapshotForecastViewReader reader;

    private static final LocalDateTime ANNOUNCE_TIME = LocalDateTime.of(2026, 1, 22, 5, 0);

    @Test
    @DisplayName("loadHourly — SnapshotReader.loadCurrent -> mapper 변환")
    void loadHourly_delegatesAndMaps() {
        WeatherSnapshot snap = new WeatherSnapshot("R1", ANNOUNCE_TIME,
                List.of(new HourlyPoint(ANNOUNCE_TIME.plusHours(1), 5, 30)), List.of());
        when(snapshotReader.loadCurrent("R1")).thenReturn(snap);

        ForecastHourlyView result = reader.loadHourly("R1");

        assertThat(result).isNotNull();
        assertThat(result.hourlyPoints()).hasSize(1);
        verify(snapshotReader).loadCurrent("R1");
    }

    @Test
    @DisplayName("loadDaily — SnapshotReader.loadCurrent -> mapper 변환")
    void loadDaily_delegatesAndMaps() {
        WeatherSnapshot snap = new WeatherSnapshot("R1", ANNOUNCE_TIME, List.of(), List.of());
        when(snapshotReader.loadCurrent("R1")).thenReturn(snap);

        ForecastDailyView result = reader.loadDaily("R1");

        assertThat(result).isNotNull();
        verify(snapshotReader).loadCurrent("R1");
    }

    @Test
    @DisplayName("스냅샷 null -> null 반환")
    void snapshotNull_returnsNull() {
        when(snapshotReader.loadCurrent("R1")).thenReturn(null);

        assertThat(reader.loadHourly("R1")).isNull();
    }
}
