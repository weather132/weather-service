package com.github.yun531.climate.service;

import com.github.yun531.climate.dto.DailyForecastDto;
import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import com.github.yun531.climate.service.forecast.AppForecastService;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppForecastServiceTest {

    @Mock
    private SnapshotQueryService snapshotQueryService;

    @InjectMocks
    private AppForecastService appForecastService;

    @Test
    @DisplayName("hourly forecast: ClimateService 가 null 을 반환하면 null 을 반환한다")
    void getHourlyForecast_returnsNull_whenClimateServiceReturnsNull() {
        String regionId = "11";
        when(snapshotQueryService.getHourlyForecast(regionId)).thenReturn(null);

        HourlyForecastDto result = appForecastService.getHourlyForecast(regionId);

        assertNull(result);
        verify(snapshotQueryService).getHourlyForecast(regionId);
    }

    @Test
    @DisplayName("hourly forecast: reportTime 이 null 이면 보정 없이 원본 그대로 반환한다")
    void adjustHourlyOffsets_returnsBase_whenReportTimeIsNull() {
        String regionId = "11";
        HourlyForecastDto base =
                new HourlyForecastDto(regionId, null,
                        List.of(new HourlyPoint(0, 10, 20)));

        when(snapshotQueryService.getHourlyForecast(regionId)).thenReturn(base);

        HourlyForecastDto result = appForecastService.getHourlyForecast(regionId);

        assertSame(base, result);
        verify(snapshotQueryService).getHourlyForecast(regionId);
    }

    @Test
    @DisplayName("hourly forecast: now 가 reportTime 보다 이전이거나 동일하면 보정 없이 그대로 반환한다")
    void adjustHourlyOffsets_returnsBase_whenNowBeforeOrEqualReportTime() {
        String regionId = "11";
        LocalDateTime reportTime = LocalDateTime.now().plusHours(1);

        HourlyForecastDto base =
                new HourlyForecastDto(regionId, reportTime,
                        List.of(
                                new HourlyPoint(0, 10, 20),
                                new HourlyPoint(1, 11, 30)
                        ));

        when(snapshotQueryService.getHourlyForecast(regionId)).thenReturn(base);

        HourlyForecastDto result = appForecastService.getHourlyForecast(regionId);

        assertSame(base, result);
        verify(snapshotQueryService).getHourlyForecast(regionId);
    }

    @Test
    @DisplayName("hourly forecast: 1시간 지난 경우 앞 1개를 제거하고 hourOffset을 1부터 재부여한다")
    void adjustHourlyOffsets_shiftsByOneHour_whenOneHourPassed() {
        String regionId = "11";

        // 서비스 내부에서 now()를 다시 찍으므로 경계(59분대)를 피하기 위해 2분 더 뺌
        LocalDateTime reportTime = LocalDateTime.now().minusHours(1).minusMinutes(2);

        // base hourOffset: 0,1,2,3
        HourlyForecastDto base =
                new HourlyForecastDto(regionId, reportTime,
                        List.of(
                                new HourlyPoint(0, 10, 20),
                                new HourlyPoint(1, 11, 30),
                                new HourlyPoint(2, 12, 40),
                                new HourlyPoint(3, 13, 50)
                        ));

        when(snapshotQueryService.getHourlyForecast(regionId)).thenReturn(base);

        HourlyForecastDto result = appForecastService.getHourlyForecast(regionId);

        List<HourlyPoint> hours = result.hours();
        assertEquals(3, hours.size(), "skip(1) 이후 3개가 남아야 한다");

        assertEquals(1, hours.get(0).hourOffset());
        assertEquals(11, hours.get(0).temp());
        assertEquals(30, hours.get(0).pop());

        assertEquals(2, hours.get(1).hourOffset());
        assertEquals(12, hours.get(1).temp());
        assertEquals(40, hours.get(1).pop());

        assertEquals(3, hours.get(2).hourOffset());
        assertEquals(13, hours.get(2).temp());
        assertEquals(50, hours.get(2).pop());

        assertEquals(regionId, result.regionId());
        assertEquals(reportTime, result.reportTime());
    }

    @Test
    @DisplayName("hourly forecast: 3시간 이상 지난 경우 최대 2시간까지만 보정하고 hourOffset을 1부터 재부여한다")
    void adjustHourlyOffsets_clampedToTwoHours_whenMoreThanTwoHoursPassed() {
        String regionId = "11";

        // rawDiffHours가 확실히 5가 되도록 2분 더 뺌
        LocalDateTime reportTime = LocalDateTime.now().minusHours(5).minusMinutes(2);

        HourlyForecastDto base =
                new HourlyForecastDto(regionId, reportTime,
                        List.of(
                                new HourlyPoint(0, 10, 20),
                                new HourlyPoint(1, 11, 30),
                                new HourlyPoint(2, 12, 40),
                                new HourlyPoint(3, 13, 50)
                        ));

        when(snapshotQueryService.getHourlyForecast(regionId)).thenReturn(base);

        HourlyForecastDto result = appForecastService.getHourlyForecast(regionId);

        // diffHours = 2 → skip(2) → (2,3)만 남고 → hourOffset을 (1,2)로 재부여
        List<HourlyPoint> hours = result.hours();
        assertEquals(2, hours.size(), "skip(2) 이후 2개가 남아야 한다");

        assertEquals(1, hours.get(0).hourOffset());
        assertEquals(12, hours.get(0).temp());
        assertEquals(40, hours.get(0).pop());

        assertEquals(2, hours.get(1).hourOffset());
        assertEquals(13, hours.get(1).temp());
        assertEquals(50, hours.get(1).pop());
    }

    @Test
    @DisplayName("daily forecast: ClimateService 의 결과를 그대로 반환한다")
    void getDailyForecast_delegatesToClimateService() {
        String regionId = "11";
        LocalDateTime reportTime = LocalDateTime.now();

        DailyForecastDto baseDaily =
                new DailyForecastDto(regionId, reportTime, List.of());

        when(snapshotQueryService.getDailyForecast(regionId)).thenReturn(baseDaily);

        DailyForecastDto result = appForecastService.getDailyForecast(regionId);

        assertSame(baseDaily, result);
        verify(snapshotQueryService).getDailyForecast(regionId);
    }
}