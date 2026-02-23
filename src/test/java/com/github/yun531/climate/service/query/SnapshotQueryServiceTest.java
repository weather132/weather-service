package com.github.yun531.climate.service.query;

import com.github.yun531.climate.shared.snapshot.port.SnapshotReadPort;
import com.github.yun531.climate.service.forecast.model.DailyPoint;
import com.github.yun531.climate.service.forecast.model.ForecastSnap;
import com.github.yun531.climate.service.forecast.model.HourlyPoint;
import com.github.yun531.climate.service.notification.model.PopView;
import com.github.yun531.climate.service.notification.model.PopViewPair;
import com.github.yun531.climate.shared.snapshot.SnapKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.github.yun531.climate.shared.time.TimeUtil.nowMinutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * SnapshotQueryService 테스트 (PopView 단일 모델 반영)
 */
@ExtendWith(MockitoExtension.class)
class SnapshotQueryServiceTest {

    @Mock
    SnapshotReadPort snapshotReadPort;

    SnapshotQueryService snapshotQueryService;

    @BeforeEach
    void setUp() {
        snapshotQueryService = new SnapshotQueryService(snapshotReadPort);
    }

    @Test
    void loadPopSeries_정상_현재이전_길이_gap_값검증() {
        // given
        String regionId = "100";
        SnapKind curKind = SnapKind.CURRENT;
        SnapKind prvKind = SnapKind.PREVIOUS;

        LocalDateTime prvTime = LocalDateTime.of(2025, 1, 1, 8, 0, 0);
        LocalDateTime curTime = prvTime.plusHours(3);

        // POP 값: current 0..25, previous 10..35
        List<Integer> curHourlyPop = rangeList(0, 26);
        List<Integer> prvHourlyPop = rangeList(10, 26);

        ForecastSnap cur = new ForecastSnap(
                regionId,
                curTime,
                buildHourlyPoints(curTime, curHourlyPop),
                dummyDailyPoints()
        );

        ForecastSnap prv = new ForecastSnap(
                regionId,
                prvTime,
                buildHourlyPoints(prvTime, prvHourlyPop),
                dummyDailyPoints()
        );

        when(snapshotReadPort.load(regionId, curKind)).thenReturn(cur);
        when(snapshotReadPort.load(regionId, prvKind)).thenReturn(prv);

        // when
        PopViewPair series = snapshotQueryService.loadPopViewPair(regionId, curKind, prvKind);

        // then
        assertThat(series.current()).isNotNull();
        assertThat(series.previous()).isNotNull();

        assertThat(series.current().hourly().points()).hasSize(26);
        assertThat(series.previous().hourly().points()).hasSize(26);

        // current: 0..25
        for (int off = 1; off <= 26; off++) {
            int pop = series.current().hourly().points().get(off - 1).pop();
            assertThat(pop).isEqualTo(off - 1);
        }

        // previous: 10..35
        for (int off = 1; off <= 26; off++) {
            int pop = series.previous().hourly().points().get(off - 1).pop();
            assertThat(pop).isEqualTo(10 + (off - 1));
        }

        // reportTimeGap = 3
        assertThat(series.reportTimeGap()).isEqualTo(3);

        // current reportTime도 같이 확인
        assertThat(series.current().reportTime()).isEqualTo(curTime);
    }

    @Test
    void loadPopSeries_스냅하나라도없으면_null과_gap0반환() {
        // given
        String regionId = "200";
        SnapKind curKind = SnapKind.CURRENT;
        SnapKind prvKind = SnapKind.PREVIOUS;

        LocalDateTime baseTime = nowMinutes();
        ForecastSnap cur = new ForecastSnap(
                regionId,
                baseTime,
                buildHourlyPoints(baseTime, rangeList(0, 26)),
                dummyDailyPoints()
        );

        when(snapshotReadPort.load(regionId, curKind)).thenReturn(cur);
        when(snapshotReadPort.load(regionId, prvKind)).thenReturn(null);

        // when
        snapshotQueryService.loadDefaultPopViewPair(regionId);

        // then
        verify(snapshotReadPort, times(1)).load(regionId, curKind);
        verify(snapshotReadPort, times(1)).load(regionId, prvKind);
        verifyNoMoreInteractions(snapshotReadPort);
    }

    @Test
    void loadDefaultPopSeries_provider호출파라미터_검증() {
        // given
        String regionId = "7";
        SnapKind curKind = SnapKind.CURRENT;
        SnapKind prvKind = SnapKind.PREVIOUS;

        LocalDateTime curTime = nowMinutes();
        LocalDateTime prvTime = curTime.minusHours(3);

        ForecastSnap cur = new ForecastSnap(
                regionId,
                curTime,
                buildHourlyPoints(curTime, rangeList(0, 26)),
                dummyDailyPoints()
        );
        ForecastSnap prv = new ForecastSnap(
                regionId,
                prvTime,
                buildHourlyPoints(prvTime, rangeList(10, 26)),
                dummyDailyPoints()
        );

        when(snapshotReadPort.load(regionId, curKind)).thenReturn(cur);
        when(snapshotReadPort.load(regionId, prvKind)).thenReturn(prv);

        // when
        snapshotQueryService.loadDefaultPopViewPair(regionId);

        // then
        verify(snapshotReadPort, times(1)).load(regionId, curKind);
        verify(snapshotReadPort, times(1)).load(regionId, prvKind);
        verifyNoMoreInteractions(snapshotReadPort);
    }

    @Test
    void loadForecastSeries_hourly와_daily_매핑검증() {
        // given
        String regionId = "55";
        SnapKind kind = SnapKind.CURRENT;

        LocalDateTime reportTime = nowMinutes();

        List<Integer> hourlyPop = rangeList(0, 26);
        List<HourlyPoint> hourly = buildHourlyPoints(reportTime, hourlyPop);

        List<DailyPoint> daily = List.of(
                new DailyPoint(0, null, null, 60, 10),
                new DailyPoint(1, null, null, 10, 70),
                new DailyPoint(2, null, null, 65, 65),
                new DailyPoint(3, null, null, 0, 0),
                new DailyPoint(4, null, null, 80, 0),
                new DailyPoint(5, null, null, 0, 80),
                new DailyPoint(6, null, null, 50, 50)
        );

        ForecastSnap snapshot = new ForecastSnap(
                regionId,
                reportTime,
                hourly,
                daily
        );

        when(snapshotReadPort.load(regionId, kind)).thenReturn(snapshot);

        // when
        PopView pop = snapshotQueryService.loadPopView(regionId, kind);

        // then
        assertThat(pop).isNotNull();
        assertThat(pop.hourly()).isNotNull();
        assertThat(pop.daily()).isNotNull();

        // hourly 0..25
        assertThat(pop.hourly().points()).hasSize(26);
        for (int i = 1; i <= 26; i++) {
            int p = pop.hourly().points().get(i - 1).pop();
            assertThat(p).isEqualTo(i - 1);
        }

        // daily 7일 AM/PM
        assertThat(pop.daily().days()).hasSize(7);
        assertThat(pop.daily().days().get(0).am()).isEqualTo(60);
        assertThat(pop.daily().days().get(0).pm()).isEqualTo(10);
        assertThat(pop.daily().days().get(1).pm()).isEqualTo(70);
        assertThat(pop.daily().days().get(4).am()).isEqualTo(80);
        assertThat(pop.daily().days().get(5).pm()).isEqualTo(80);
    }

    @Test
    void loadForecastSeries_snapshot없으면_null반환() {
        // given
        String regionId = "99";
        SnapKind kind = SnapKind.CURRENT;

        when(snapshotReadPort.load(regionId, kind)).thenReturn(null);

        // when
        PopView pop = snapshotQueryService.loadPopView(regionId, kind);

        // then
        assertThat(pop).isNull();
    }

    // ---- helpers ----

    private static List<Integer> rangeList(int base, int count) {
        List<Integer> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) out.add(base + i);
        return out;
    }

    /**
     * POP만 필요한 경우 temp=null, pop만 채운 HourlyPoint 리스트 생성
     * - validAt은 reportTime + (1..N)시간
     */
    private static List<HourlyPoint> buildHourlyPoints(LocalDateTime reportTime, List<Integer> pops) {
        List<HourlyPoint> list = new ArrayList<>(pops.size());
        for (int i = 0; i < pops.size(); i++) {
            LocalDateTime validAt = reportTime.plusHours(i + 1L);
            list.add(new HourlyPoint(validAt, null, pops.get(i)));
        }
        return List.copyOf(list);
    }

    private static List<DailyPoint> dummyDailyPoints() {
        List<DailyPoint> list = new ArrayList<>(7);
        for (int day = 0; day < 7; day++) {
            list.add(new DailyPoint(day, null, null, null, null));
        }
        return List.copyOf(list);
    }
}