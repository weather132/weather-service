package com.github.yun531.climate.service;

import com.github.yun531.climate.dto.DailyForecastDto;
import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.dto.HourlyForecastDto.HourlyForecastEntry;
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

/**
 * AppForecastService 단위 테스트
 */
@ExtendWith(MockitoExtension.class)
class AppForecastServiceTest {

    @Mock
    private ClimateService climateService;

    @InjectMocks
    private AppForecastService appForecastService;

    @Test
    @DisplayName("hourly forecast: ClimateService 가 null 을 반환하면 null 을 반환한다")
    void getHourlyForecast_returnsNull_whenClimateServiceReturnsNull() {
        int regionId = 11;
        when(climateService.getHourlyForecast(regionId)).thenReturn(null);

        HourlyForecastDto result = appForecastService.getHourlyForecast(regionId);

        assertNull(result);
        verify(climateService).getHourlyForecast(regionId);
    }

    @Test
    @DisplayName("hourly forecast: reportTime 이 null 이면 보정 없이 원본 그대로 반환한다")
    void adjustHourlyOffsets_returnsBase_whenReportTimeIsNull() {
        int regionId = 11;
        HourlyForecastDto base =
                new HourlyForecastDto(regionId, null,
                        List.of(new HourlyForecastEntry(0, 10, 20)));

        when(climateService.getHourlyForecast(regionId)).thenReturn(base);

        HourlyForecastDto result = appForecastService.getHourlyForecast(regionId);

        // reportTime 이 null 이면 adjustHourlyOffsets 가 그대로 base 를 반환
        assertSame(base, result);
        verify(climateService).getHourlyForecast(regionId);
    }

    @Test
    @DisplayName("hourly forecast: now 가 reportTime 보다 이전이거나 동일하면 보정 없이 그대로 반환한다")
    void adjustHourlyOffsets_returnsBase_whenNowBeforeOrEqualReportTime() {
        int regionId = 11;
        LocalDateTime now = LocalDateTime.now();
        // reportTime 을 미래로 설정 (now + 1h) → diffHours <= 0
        LocalDateTime reportTime = now.plusHours(1);

        HourlyForecastDto base =
                new HourlyForecastDto(regionId, reportTime,
                        List.of(
                                new HourlyForecastEntry(0, 10, 20),
                                new HourlyForecastEntry(1, 11, 30)
                        ));

        when(climateService.getHourlyForecast(regionId)).thenReturn(base);

        HourlyForecastDto result = appForecastService.getHourlyForecast(regionId);

        // 미래 기준이므로 보정 없이 원본 그대로
        assertSame(base, result);
        verify(climateService).getHourlyForecast(regionId);
    }

    @Test
    @DisplayName("hourly forecast: 1시간 지난 경우 1~23시간 데이터를 0~22시간으로 보정한다")
    void adjustHourlyOffsets_shiftsByOneHour_whenOneHourPassed() {
        int regionId = 11;
        LocalDateTime now = LocalDateTime.now();
        // reportTime 을 now - 1h 로 설정 → diffHours ≈ 1
        LocalDateTime reportTime = now.minusHours(1);

        // hourOffset: 0,1,2,3 → 1시간 지났으니 1..3 → 0..2 로 재라벨링되어야 함
        HourlyForecastDto base =
                new HourlyForecastDto(regionId, reportTime,
                        List.of(
                                new HourlyForecastEntry(0, 10, 20),
                                new HourlyForecastEntry(1, 11, 30),
                                new HourlyForecastEntry(2, 12, 40),
                                new HourlyForecastEntry(3, 13, 50)
                        ));

        when(climateService.getHourlyForecast(regionId)).thenReturn(base);

        HourlyForecastDto result = appForecastService.getHourlyForecast(regionId);

        // 결과 검증
        List<HourlyForecastEntry> hours = result.hours();
        assertEquals(3, hours.size(), "0~2 총 3개가 남아야 한다");

        assertEquals(0, hours.get(0).hourOffset());
        assertEquals(11, hours.get(0).temp());
        assertEquals(30, hours.get(0).pop());

        assertEquals(1, hours.get(1).hourOffset());
        assertEquals(12, hours.get(1).temp());
        assertEquals(40, hours.get(1).pop());

        assertEquals(2, hours.get(2).hourOffset());
        assertEquals(13, hours.get(2).temp());
        assertEquals(50, hours.get(2).pop());

        // regionId / reportTime 은 그대로 유지
        assertEquals(regionId, result.regionId());
        assertEquals(reportTime, result.reportTime());
    }

    @Test
    @DisplayName("hourly forecast: 3시간 이상 지난 경우 최대 2시간까지만 보정한다")
    void adjustHourlyOffsets_clampedToTwoHours_whenMoreThanTwoHoursPassed() {
        int regionId = 11;
        LocalDateTime now = LocalDateTime.now();
        // reportTime 을 now - 5h 로 설정 → rawDiffHours ≈ 5 → diffHours = 2 로 클램핑
        LocalDateTime reportTime = now.minusHours(5);

        HourlyForecastDto base =
                new HourlyForecastDto(regionId, reportTime,
                        List.of(
                                new HourlyForecastEntry(0, 10, 20),
                                new HourlyForecastEntry(1, 11, 30),
                                new HourlyForecastEntry(2, 12, 40),
                                new HourlyForecastEntry(3, 13, 50)
                        ));

        when(climateService.getHourlyForecast(regionId)).thenReturn(base);

        HourlyForecastDto result = appForecastService.getHourlyForecast(regionId);

        // diffHours = 2 → offset: 0,1,2,3 → -2,-1,0,1 → 0,1만 남음
        List<HourlyForecastEntry> hours = result.hours();
        assertEquals(2, hours.size(), "0~1 두 개만 남아야 한다");

        assertEquals(0, hours.get(0).hourOffset());
        assertEquals(12, hours.get(0).temp());
        assertEquals(40, hours.get(0).pop());

        assertEquals(1, hours.get(1).hourOffset());
        assertEquals(13, hours.get(1).temp());
        assertEquals(50, hours.get(1).pop());
    }

    @Test
    @DisplayName("daily forecast: ClimateService 의 결과를 그대로 반환한다")
    void getDailyForecast_delegatesToClimateService() {
        int regionId = 11;
        LocalDateTime reportTime = LocalDateTime.now();

        DailyForecastDto baseDaily =
                new DailyForecastDto(regionId, reportTime, List.of());

        when(climateService.getDailyForecast(regionId)).thenReturn(baseDaily);

        DailyForecastDto result = appForecastService.getDailyForecast(regionId);

        assertSame(baseDaily, result);
        verify(climateService).getDailyForecast(regionId);
    }
}