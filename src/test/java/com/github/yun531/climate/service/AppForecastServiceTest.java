package com.github.yun531.climate.service;

import com.github.yun531.climate.dto.DailyForecastDto;
import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.service.forecast.AppForecastService;
import com.github.yun531.climate.service.forecast.HourlyForecastWindowAdjuster;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppForecastServiceTest {

    private static final int MAX_SHIFT_HOURS = 2;
    private static final int WINDOW_SIZE = 24;

    @Mock
    private SnapshotQueryService snapshotQueryService;

    private AppForecastService appForecastService;

    @BeforeEach
    void setUp() {
        // 단위테스트에서는 Spring @Bean(ForecastConfig)이 없으므로 직접 주입
        HourlyForecastWindowAdjuster adjuster =
                new HourlyForecastWindowAdjuster(MAX_SHIFT_HOURS, WINDOW_SIZE);

        appForecastService = new AppForecastService(snapshotQueryService, adjuster);
    }

    @Test
    @DisplayName("hourly forecast: SnapshotQueryService가 null을 반환하면 null을 반환한다")
    void getHourlyForecast_returnsNull_whenSnapshotQueryReturnsNull() {
        String regionId = "11";
        when(snapshotQueryService.getHourlyForecast(regionId)).thenReturn(null);

        LocalDateTime now = LocalDateTime.of(2026, 2, 10, 10, 0);
        HourlyForecastDto result = appForecastService.computeForRegion(regionId, now);

        assertNull(result);
        verify(snapshotQueryService).getHourlyForecast(regionId);
    }

    @Test
    @DisplayName("hourly forecast: reportTime이 null이면 보정 없이(정렬만) 반환한다")
    void adjustHourly_returnsBase_whenReportTimeIsNull() {
        String regionId = "11";

        HourlyForecastDto base =
                new HourlyForecastDto(
                        regionId,
                        null,
                        List.of(new HourlyPoint(null, 10, 20))
                );

        when(snapshotQueryService.getHourlyForecast(regionId)).thenReturn(base);

        LocalDateTime now = LocalDateTime.of(2026, 2, 10, 10, 0);
        HourlyForecastDto result = appForecastService.computeForRegion(regionId, now);

        assertNotNull(result);
        assertEquals(base, result);
        verify(snapshotQueryService).getHourlyForecast(regionId);
    }

    @Test
    @DisplayName("hourly forecast: now가 reportTime보다 이전/동일이면 reportTime 유지 + validAt 기준 정렬만 적용된다")
    void adjustHourly_returnsSorted_whenNowBeforeOrEqualReportTime() {
        String regionId = "11";

        LocalDateTime reportTime = LocalDateTime.of(2026, 2, 10, 11, 10);
        LocalDateTime now = LocalDateTime.of(2026, 2, 10, 11, 0); // reportTime보다 이전 -> diffHours<=0

        LocalDateTime t1 = reportTime.plusHours(1);
        LocalDateTime t2 = reportTime.plusHours(2);

        HourlyForecastDto base =
                new HourlyForecastDto(
                        regionId,
                        reportTime,
                        List.of(
                                new HourlyPoint(t2, 12, 40),
                                new HourlyPoint(t1, 11, 30)
                        )
                );

        when(snapshotQueryService.getHourlyForecast(regionId)).thenReturn(base);

        HourlyForecastDto result = appForecastService.computeForRegion(regionId, now);

        assertNotNull(result);
        assertEquals(regionId, result.regionId());
        assertEquals(reportTime, result.reportTime());

        assertEquals(2, result.hours().size());
        assertEquals(t1, result.hours().get(0).validAt());
        assertEquals(11, result.hours().get(0).temp());
        assertEquals(30, result.hours().get(0).pop());

        assertEquals(t2, result.hours().get(1).validAt());
        assertEquals(12, result.hours().get(1).temp());
        assertEquals(40, result.hours().get(1).pop());
    }

    @Test
    @DisplayName("hourly forecast: 1시간 경과 시 reportTime이 +1h 보정되고, validAt >= 보정 reportTime 인 항목만 남는다")
    void adjustHourly_shiftsByOneHour_usingValidAt() {
        String regionId = "11";

        // baseHour=10:00, nowHour=11:00 -> diffHours=1
        LocalDateTime reportTime = LocalDateTime.of(2026, 2, 10, 10, 0);
        LocalDateTime now        = LocalDateTime.of(2026, 2, 10, 11, 0);

        LocalDateTime v0 = reportTime;              // 10:00 (제외)
        LocalDateTime v1 = reportTime.plusHours(1); // 11:00 (미포함, shiftedReportTime)
        LocalDateTime v2 = reportTime.plusHours(2); // 12:00
        LocalDateTime v3 = reportTime.plusHours(3); // 13:00

        HourlyForecastDto base =
                new HourlyForecastDto(
                        regionId,
                        reportTime,
                        List.of(
                                new HourlyPoint(v0, 10, 20),
                                new HourlyPoint(v1, 11, 30),
                                new HourlyPoint(v2, 12, 40),
                                new HourlyPoint(v3, 13, 50)
                        )
                );

        when(snapshotQueryService.getHourlyForecast(regionId)).thenReturn(base);

        HourlyForecastDto result = appForecastService.computeForRegion(regionId, now);

        assertNotNull(result);
        assertEquals(regionId, result.regionId());

        assertEquals(reportTime.truncatedTo(ChronoUnit.HOURS).plusHours(1), result.reportTime());

        List<HourlyPoint> hours = result.hours();
        assertEquals(2, hours.size());

        assertEquals(v2, hours.get(0).validAt());
        assertEquals(12, hours.get(0).temp());
        assertEquals(40, hours.get(0).pop());

        assertEquals(v3, hours.get(1).validAt());
        assertEquals(13, hours.get(1).temp());
        assertEquals(50, hours.get(1).pop());
    }

    @Test
    @DisplayName("hourly forecast: 3시간 이상 경과 시 최대 2시간까지만 보정하고, validAt > (reportTime+2h) 인 항목만 남는다")
    void adjustHourly_clampedToTwoHours_usingValidAt() {
        String regionId = "11";

        // baseHour=10:00, nowHour=14:00 -> raw=4, clamp=2
        LocalDateTime reportTime = LocalDateTime.of(2026, 2, 10, 10, 10);
        LocalDateTime now        = LocalDateTime.of(2026, 2, 10, 14, 0);

        LocalDateTime v0 = reportTime;              // 10:10
        LocalDateTime v1 = reportTime.plusHours(1); // 11:10
        LocalDateTime v2 = reportTime.plusHours(2); // 12:10 (포함, shiftedReportTime)
        LocalDateTime v3 = reportTime.plusHours(3); // 13:10

        HourlyForecastDto base =
                new HourlyForecastDto(
                        regionId,
                        reportTime,
                        List.of(
                                new HourlyPoint(v0, 10, 20),
                                new HourlyPoint(v1, 11, 30),
                                new HourlyPoint(v2, 12, 40),
                                new HourlyPoint(v3, 13, 50)
                        )
                );

        when(snapshotQueryService.getHourlyForecast(regionId)).thenReturn(base);

        HourlyForecastDto result = appForecastService.computeForRegion(regionId, now);

        assertNotNull(result);

        assertEquals(reportTime.truncatedTo(ChronoUnit.HOURS).plusHours(2), result.reportTime());

        List<HourlyPoint> hours = result.hours();
        assertEquals(2, hours.size());

        assertEquals(v2, hours.get(0).validAt());
        assertEquals(12, hours.get(0).temp());
        assertEquals(40, hours.get(0).pop());

        assertEquals(v3, hours.get(1).validAt());
        assertEquals(13, hours.get(1).temp());
        assertEquals(50, hours.get(1).pop());
    }

    @Test
    @DisplayName("daily forecast: SnapshotQueryService의 결과를 그대로 반환한다")
    void getDailyForecast_delegatesToSnapshotQueryService() {
        String regionId = "11";
        LocalDateTime reportTime = LocalDateTime.of(2026, 2, 10, 10, 0);

        DailyForecastDto baseDaily =
                new DailyForecastDto(regionId, reportTime, List.of());

        when(snapshotQueryService.getDailyForecast(regionId)).thenReturn(baseDaily);

        DailyForecastDto result = appForecastService.getDailyForecast(regionId);

        assertSame(baseDaily, result);
        verify(snapshotQueryService).getDailyForecast(regionId);
    }

    @Test
    @DisplayName("hourly forecast: 생성시간과 현재시간이 동일하면(=) 보정 없이 reportTime 유지 + validAt 정렬만 적용된다")
    void computeForRegion_noShift_whenNowEqualsReportTime() {
        String regionId = "11";

        LocalDateTime reportTime = LocalDateTime.of(2026, 2, 10, 10, 0);
        LocalDateTime now        = LocalDateTime.of(2026, 2, 10, 10, 0);

        LocalDateTime t1 = reportTime.plusHours(1); // 11:00
        LocalDateTime t2 = reportTime.plusHours(2); // 12:00

        // 일부러 역순으로 넣어서 정렬만 적용되는지 확인
        HourlyForecastDto base =
                new HourlyForecastDto(
                        regionId,
                        reportTime,
                        List.of(
                                new HourlyPoint(t2, 12, 40),
                                new HourlyPoint(t1, 11, 30)
                        )
                );

        when(snapshotQueryService.getHourlyForecast(regionId)).thenReturn(base);

        HourlyForecastDto result = appForecastService.computeForRegion(regionId, now);

        assertNotNull(result);
        assertEquals(regionId, result.regionId());
        assertEquals(reportTime, result.reportTime());

        assertEquals(2, result.hours().size());
        assertEquals(t1, result.hours().get(0).validAt());
        assertEquals(11, result.hours().get(0).temp());
        assertEquals(30, result.hours().get(0).pop());

        assertEquals(t2, result.hours().get(1).validAt());
        assertEquals(12, result.hours().get(1).temp());
        assertEquals(40, result.hours().get(1).pop());

        verify(snapshotQueryService).getHourlyForecast(regionId);
    }

    @Test
    @DisplayName("hourly forecast: 1시간 지난 경우 reportTime이 +1h 보정되고, validAt > 보정 reportTime 인 항목만 남는다")
    void computeForRegion_shiftsByOneHour_exclusiveUsingValidAt() {
        String regionId = "11";

        LocalDateTime reportTime = LocalDateTime.of(2026, 2, 10, 10, 0);
        LocalDateTime now        = LocalDateTime.of(2026, 2, 10, 11, 0); // 1시간 경과 -> diffHours=1

        LocalDateTime v0 = reportTime;              // 10:00
        LocalDateTime v1 = reportTime.plusHours(1); // 11:00 (shiftedBaseTime과 동일 -> 초과(>) 정책이면 제외)
        LocalDateTime v2 = reportTime.plusHours(2); // 12:00 (포함)

        HourlyForecastDto base =
                new HourlyForecastDto(
                        regionId,
                        reportTime,
                        List.of(
                                new HourlyPoint(v0, 10, 20),
                                new HourlyPoint(v1, 11, 30),
                                new HourlyPoint(v2, 12, 40)
                        )
                );

        when(snapshotQueryService.getHourlyForecast(regionId)).thenReturn(base);

        HourlyForecastDto result = appForecastService.computeForRegion(regionId, now);

        assertNotNull(result);
        assertEquals(regionId, result.regionId());

        // 1시간 보정
        assertEquals(reportTime.plusHours(1), result.reportTime());

        // 초과(>) 정책: v1(==shiftedBaseTime)은 제외되고 v2만 남음
        List<HourlyPoint> hours = result.hours();
        assertEquals(1, hours.size());

        assertEquals(v2, hours.get(0).validAt());
        assertEquals(12, hours.get(0).temp());
        assertEquals(40, hours.get(0).pop());

        verify(snapshotQueryService).getHourlyForecast(regionId);
    }
}