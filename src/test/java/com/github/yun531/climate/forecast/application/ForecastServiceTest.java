package com.github.yun531.climate.forecast.application;

import com.github.yun531.climate.forecast.domain.adjust.ForecastWindowAdjuster;
import com.github.yun531.climate.forecast.domain.reader.ForecastViewReader;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastDailyPoint;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastDailyView;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastHourlyPoint;
import com.github.yun531.climate.forecast.domain.readmodel.ForecastHourlyView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ForecastServiceTest {

    @Mock ForecastViewReader viewReader;
    @Mock
    ForecastWindowAdjuster windowAdjuster;

    private ForecastService service;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 22, 5, 15);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    @BeforeEach
    void setUp() {
        service = new ForecastService(viewReader, windowAdjuster, FIXED_CLOCK);
    }

    @Test
    @DisplayName("getHourlyForecast — reader 로드 -> adjuster 적용")
    void hourly_readerThenAdjuster() {
        ForecastHourlyView base = new ForecastHourlyView("R1", NOW, List.of(
                new ForecastHourlyPoint(NOW.plusHours(1), 10, 20),
                new ForecastHourlyPoint(NOW.plusHours(2), 12, 30)
        ));
        ForecastHourlyView adjusted = new ForecastHourlyView("R1", NOW, List.of(
                new ForecastHourlyPoint(NOW.plusHours(2), 12, 30)   // 윈도우 적용 후 1개만 남음
        ));

        when(viewReader.loadHourly("R1")).thenReturn(base);
        when(windowAdjuster.adjust(eq(base), any(LocalDateTime.class))).thenReturn(adjusted);

        ForecastHourlyView result = service.getHourlyForecast("R1");

        assertThat(result).isSameAs(adjusted);
        assertThat(result.hourlyPoints()).hasSize(1);
    }

    @Test
    @DisplayName("getHourlyForecast — reader가 null 반환 -> null")
    void hourly_readerReturnsNull() {
        when(viewReader.loadHourly("R1")).thenReturn(null);

        assertThat(service.getHourlyForecast("R1")).isNull();
        verify(windowAdjuster, never()).adjust(any(), any());
    }

    @Test
    @DisplayName("getDailyForecast — reader 로드만 수행")
    void daily_delegatesToReader() {
        ForecastDailyView view = new ForecastDailyView("R1", NOW, List.of(
                new ForecastDailyPoint(0, -5, 5, 30, 60)
        ));
        when(viewReader.loadDaily("R1")).thenReturn(view);

        ForecastDailyView result = service.getDailyForecast("R1");

        assertThat(result).isEqualTo(view);
        verify(viewReader).loadDaily("R1");
    }
}
