package com.github.yun531.climate.service;

import com.github.yun531.climate.dto.DailyForecastDto;
import com.github.yun531.climate.dto.HourlyForecastDto;
import com.github.yun531.climate.service.forecast.AppForecastService;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import com.github.yun531.climate.util.time.TimeUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    @DisplayName("hourly forecast: SnapshotQueryService가 null을 반환하면 null을 반환한다")
    void getHourlyForecast_returnsNull_whenSnapshotQueryReturnsNull() {
        String regionId = "11";
        when(snapshotQueryService.getHourlyForecast(regionId)).thenReturn(null);

        HourlyForecastDto result = appForecastService.getHourlyForecast(regionId);

        assertNull(result);
        verify(snapshotQueryService).getHourlyForecast(regionId);
    }

    @Test
    @DisplayName("hourly forecast: reportTime이 null이면 보정 없이(정규화만) 반환한다")
    void adjustHourly_returnsBase_whenReportTimeIsNull() {
        String regionId = "11";

        HourlyForecastDto base =
                new HourlyForecastDto(
                        regionId,
                        null,
                        List.of(new HourlyPoint(null, 10, 20))
                );

        when(snapshotQueryService.getHourlyForecast(regionId)).thenReturn(base);

        HourlyForecastDto result = appForecastService.getHourlyForecast(regionId);

        // 리팩토링 버전: 새 DTO로 감싸서 반환할 수 있으니 "동일 객체"가 아니라 "동일 값"을 검증
        assertNotNull(result);
        assertEquals(base, result);
        verify(snapshotQueryService).getHourlyForecast(regionId);
    }

    @Test
    @DisplayName("hourly forecast: now가 reportTime보다 이전/동일이면 reportTime 유지 + validAt 기준 정렬만 적용된다")
    void adjustHourly_returnsSorted_whenNowBeforeOrEqualReportTime() {
        String regionId = "11";

        // service 내부에서 LocalDateTime.now()를 쓰지만,
        // reportTime을 충분히 미래로 잡으면 diffHours<=0이 안정적
        LocalDateTime now = TimeUtil.nowMinutes();
        LocalDateTime reportTime = now.plusHours(1).plusMinutes(5);

        LocalDateTime t1 = reportTime.plusHours(1);
        LocalDateTime t2 = reportTime.plusHours(2);

        // 일부러 역순으로 넣음
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

        HourlyForecastDto result = appForecastService.getHourlyForecast(regionId);

        assertNotNull(result);
        assertEquals(regionId, result.regionId());
        assertEquals(reportTime, result.reportTime());

        // validAt 오름차순 정렬 확인
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

        // 서비스는 TimeUtil.nowMinutes()를 쓰므로, 테스트도 같은 축으로 맞추는 게 안정적
        LocalDateTime nowHour = TimeUtil.nowMinutes().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime reportTime = nowHour.minusHours(1).plusMinutes(10); // diffHours=1

        LocalDateTime v0 = reportTime;
        LocalDateTime v1 = reportTime.plusHours(1); // shiftedReportTime과 동일
        LocalDateTime v2 = reportTime.plusHours(2);
        LocalDateTime v3 = reportTime.plusHours(3);

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

        HourlyForecastDto result = appForecastService.getHourlyForecast(regionId);

        assertNotNull(result);
        assertEquals(regionId, result.regionId());

        // reportTime + 1h
        assertEquals(reportTime.plusHours(1), result.reportTime());

        List<HourlyPoint> hours = result.hours();

        // 리팩토링 버전은 inclusive(>=) 이라 v1도 포함됨
        assertEquals(3, hours.size());

        assertEquals(v1, hours.get(0).validAt());
        assertEquals(11, hours.get(0).temp());
        assertEquals(30, hours.get(0).pop());

        assertEquals(v2, hours.get(1).validAt());
        assertEquals(12, hours.get(1).temp());
        assertEquals(40, hours.get(1).pop());

        assertEquals(v3, hours.get(2).validAt());
        assertEquals(13, hours.get(2).temp());
        assertEquals(50, hours.get(2).pop());
    }

    @Test
    @DisplayName("hourly forecast: 3시간 이상 경과 시 최대 2시간까지만 보정하고, validAt >= (reportTime+2h) 인 항목만 남는다")
    void adjustHourly_clampedToTwoHours_usingValidAt() {
        String regionId = "11";

        LocalDateTime nowHour = TimeUtil.nowMinutes().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime reportTime = nowHour.minusHours(4).plusMinutes(10); // rawDiffHours 크게

        LocalDateTime v0 = reportTime;
        LocalDateTime v1 = reportTime.plusHours(1);
        LocalDateTime v2 = reportTime.plusHours(2); // shiftedReportTime과 동일
        LocalDateTime v3 = reportTime.plusHours(3);

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

        HourlyForecastDto result = appForecastService.getHourlyForecast(regionId);

        assertNotNull(result);

        // clamp=2 -> reportTime + 2h
        assertEquals(reportTime.plusHours(2), result.reportTime());

        List<HourlyPoint> hours = result.hours();

        // inclusive(>=) 이라 v2도 포함되어 2개가 남음
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
        LocalDateTime reportTime = TimeUtil.nowMinutes();

        DailyForecastDto baseDaily =
                new DailyForecastDto(regionId, reportTime, List.of());

        when(snapshotQueryService.getDailyForecast(regionId)).thenReturn(baseDaily);

        DailyForecastDto result = appForecastService.getDailyForecast(regionId);

        assertSame(baseDaily, result);
        verify(snapshotQueryService).getDailyForecast(regionId);
    }
}