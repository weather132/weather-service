package com.github.yun531.climate.service;

import com.github.yun531.climate.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.github.yun531.climate.util.TimeUtil.nowMinutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * ForecastSnapshotProvider 기반으로 리팩토링된 ClimateService 테스트
 */
@ExtendWith(MockitoExtension.class)
class ClimateServiceTest {

    @Mock
    SnapshotProvider snapshotProvider;

    ClimateService climateService;

    @BeforeEach
    void setUp() {
        climateService = new ClimateService(snapshotProvider);
    }

    @Test
    void loadPopSeries_정상_현재이전_길이_gap_값검증() {
        // given
        int regionId = 100;
        int curId = SnapKindEnum.SNAP_CURRENT.getCode();
        int prvId = SnapKindEnum.SNAP_PREVIOUS.getCode();

        // 이전 리포트 시각: 2025-01-01T08:00
        LocalDateTime prvTime = LocalDateTime.of(2025, 1, 1, 8, 0, 0);
        // 현재 리포트 시각: 3시간 뒤
        LocalDateTime curTime = prvTime.plusHours(3);

        // POP 값: current 0..23, previous 10..33
        List<Integer> curHourlyPop = rangeList(0, 24);
        List<Integer> prvHourlyPop = rangeList(10, 24);

        ForecastSnapshot cur = new ForecastSnapshot(
                regionId,
                curTime,
                buildHourlyPoints(curHourlyPop),      // temp는 null, pop만 유의미
                dummyDailyPoints()                    // 이 테스트에서는 daily는 사용 안 함
        );

        ForecastSnapshot prv = new ForecastSnapshot(
                regionId,
                prvTime,
                buildHourlyPoints(prvHourlyPop),
                dummyDailyPoints()
        );

        when(snapshotProvider.loadSnapshot(regionId, curId)).thenReturn(cur);
        when(snapshotProvider.loadSnapshot(regionId, prvId)).thenReturn(prv);

        // when
        PopSeriesPair series = climateService.loadPopSeries(regionId, curId, prvId);

        // then
        assertThat(series.current()).isNotNull();
        assertThat(series.previous()).isNotNull();

        assertThat(series.current().size()).isEqualTo(24);
        assertThat(series.previous().size()).isEqualTo(24);

        // current: 0..23
        for (int off = 1; off <= 24; off++) {
            assertThat(series.current().get(off)).isEqualTo(off - 1);
        }
        // previous: 10..33
        for (int off = 1; off <= 24; off++) {
            assertThat(series.previous().get(off)).isEqualTo(10 + (off - 1));
        }

        // 리포트 시각 차이(3시간)가 reportTimeGap 으로 반영되었는지 확인
        assertThat(series.reportTimeGap()).isEqualTo(3);
    }

    @Test
    void loadPopSeries_스냅하나라도없으면_null과_gap0반환() {
        // given
        int regionId = 200;
        int curId = SnapKindEnum.SNAP_CURRENT.getCode();
        int prvId = SnapKindEnum.SNAP_PREVIOUS.getCode();

        ForecastSnapshot cur = new ForecastSnapshot(
                regionId,
                nowMinutes(),
                buildHourlyPoints(rangeList(0, 24)),
                dummyDailyPoints()
        );

        // 이전 스냅이 없는 경우 (cur 만 반환)
        when(snapshotProvider.loadSnapshot(regionId, curId)).thenReturn(cur);
        when(snapshotProvider.loadSnapshot(regionId, prvId)).thenReturn(null);

        // when
        PopSeriesPair series = climateService.loadPopSeries(regionId, curId, prvId);

        // then
        assertThat(series.current()).isNull();
        assertThat(series.previous()).isNull();
        assertThat(series.reportTimeGap()).isEqualTo(0);
    }

    @Test
    void loadDefaultPopSeries_provider호출파라미터_검증() {
        // given
        int regionId = 7;
        int curId = SnapKindEnum.SNAP_CURRENT.getCode();
        int prvId = SnapKindEnum.SNAP_PREVIOUS.getCode();

        ForecastSnapshot cur = new ForecastSnapshot(
                regionId,
                nowMinutes(),
                buildHourlyPoints(rangeList(0, 24)),
                dummyDailyPoints()
        );
        ForecastSnapshot prv = new ForecastSnapshot(
                regionId,
                nowMinutes().minusHours(3),
                buildHourlyPoints(rangeList(10, 24)),
                dummyDailyPoints()
        );

        when(snapshotProvider.loadSnapshot(regionId, curId)).thenReturn(cur);
        when(snapshotProvider.loadSnapshot(regionId, prvId)).thenReturn(prv);

        // when
        climateService.loadDefaultPopSeries(regionId);

        // then
        verify(snapshotProvider, times(1)).loadSnapshot(regionId, curId);
        verify(snapshotProvider, times(1)).loadSnapshot(regionId, prvId);
        verifyNoMoreInteractions(snapshotProvider);
    }

    @Test
    void loadForecastSeries_hourly와_daily_매핑검증() {
        // given
        int regionId = 55;
        int snapId = SnapKindEnum.SNAP_CURRENT.getCode();

        // hourly POP: 0..23, temp는 null로 두고 POP만 검증
        List<Integer> hourlyPop = rangeList(0, 24);
        List<HourlyPoint> hourly = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            hourly.add(new HourlyPoint(hour, null, hourlyPop.get(hour)));
        }

        // daily: 7일치 AM/PM POP
        // PopDailySeries7 테스트와 동일한 패턴
        List<DailyPoint> daily = List.of(
                new DailyPoint(0, null, null, 60, 10),
                new DailyPoint(1, null, null, 10, 70),
                new DailyPoint(2, null, null, 65, 65),
                new DailyPoint(3, null, null, 0, 0),
                new DailyPoint(4, null, null, 80, 0),
                new DailyPoint(5, null, null, 0, 80),
                new DailyPoint(6, null, null, 50, 50)
        );

        ForecastSnapshot snapshot = new ForecastSnapshot(
                regionId,
                nowMinutes(),
                hourly,
                daily
        );

        when(snapshotProvider.loadSnapshot(regionId, snapId)).thenReturn(snapshot);

        // when
        PopForecastSeries fs = climateService.loadForecastSeries(regionId, snapId);

        // then
        assertThat(fs.hourly()).isNotNull();
        assertThat(fs.daily()).isNotNull();

        // hourly 0..23
        assertThat(fs.hourly().size()).isEqualTo(24);
        for (int i = 1; i <= 24; i++) {
            assertThat(fs.hourly().get(i)).isEqualTo(i-1);
        }

        // daily 7일 AM/PM
        assertThat(fs.daily().days()).hasSize(7);
        assertThat(fs.daily().days().get(0).am()).isEqualTo(60);
        assertThat(fs.daily().days().get(0).pm()).isEqualTo(10);
        assertThat(fs.daily().days().get(1).pm()).isEqualTo(70);
        assertThat(fs.daily().days().get(4).am()).isEqualTo(80);
        assertThat(fs.daily().days().get(5).pm()).isEqualTo(80);
    }

    @Test
    void loadForecastSeries_snapshot없으면_null들_반환() {
        // given
        int regionId = 99;
        int snapId = SnapKindEnum.SNAP_CURRENT.getCode();

        when(snapshotProvider.loadSnapshot(regionId, snapId)).thenReturn(null);

        // when
        PopForecastSeries fs = climateService.loadForecastSeries(regionId, snapId);

        // then
        assertThat(fs.hourly()).isNull();
        assertThat(fs.daily()).isNull();
    }

    // ---- helpers ----

    /** base부터 base+count-1 까지 int 리스트 생성 */
    private static List<Integer> rangeList(int base, int count) {
        List<Integer> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(base + i);
        }
        return out;
    }

    /** POP만 필요한 경우 temp=null, pop만 채운 HourlyPoint 리스트 생성 */
    private static List<HourlyPoint> buildHourlyPoints(List<Integer> pops) {
        List<HourlyPoint> list = new ArrayList<>(pops.size());
        for (int i = 0; i < pops.size(); i++) {
            list.add(new HourlyPoint(i, null, pops.get(i)));
        }
        return list;
    }

    /** 테스트에서 daily를 쓰지 않을 때 사용할 더미 7일치 DailyPoint */
    private static List<DailyPoint> dummyDailyPoints() {
        List<DailyPoint> list = new ArrayList<>(7);
        for (int day = 0; day < 7; day++) {
            list.add(new DailyPoint(day, null, null, null, null));
        }
        return list;
    }
}