package com.github.yun531.climate.service;

import com.github.yun531.climate.domain.PopDailySeries7;
import com.github.yun531.climate.domain.PopSeries24;
import com.github.yun531.climate.dto.POPSnapDto;
import com.github.yun531.climate.repository.ClimateSnapRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClimateServiceTest {

    @Mock
    ClimateSnapRepository climateSnapRepository;

    ClimateService climateService;

    @BeforeEach
    void setUp() {
        climateService = new ClimateService(climateSnapRepository);
    }

    @Test
    void loadPopSeries_정상_현재이전_길이_gap_값검증() {
        // given
        Long regionId = 100L;
        Long curId = 1L;
        Long prvId = 10L;

        // 이전 리포트 시각: 2025-01-01T08:00
        LocalDateTime prvTime = LocalDateTime.of(2025, 1, 1, 8, 0,0);
        // 현재 리포트 시각: 3시간 뒤
        LocalDateTime curTime = prvTime.plusHours(3);

        // current: 0..23
        List<Integer> curHourly = rangeList(0, 24);      // [0,1,...,23]
        // previous: 10..33
        List<Integer> prvHourly = rangeList(10, 24);     // [10,11,...,33]

        POPSnapDto cur = new POPSnapDto();
        cur.setSnapId(curId);
        cur.setRegionId(regionId);
        cur.setReportTime(curTime);
        cur.setHourly(new PopSeries24(curHourly));

        POPSnapDto prv = new POPSnapDto();
        prv.setSnapId(prvId);
        prv.setRegionId(regionId);
        prv.setReportTime(prvTime);
        prv.setHourly(new PopSeries24(prvHourly));

        // 일부러 순서를 뒤집어서 반환해도 snapId 로 제대로 매핑되는지 확인
        when(climateSnapRepository.findPopInfoBySnapIdsAndRegionId(List.of(curId, prvId), regionId))
                .thenReturn(List.of(prv, cur));

        // when
        ClimateService.PopSeries series = climateService.loadPopSeries(regionId, curId, prvId);

        // then
        assertThat(series.current()).isNotNull();
        assertThat(series.previous()).isNotNull();

        assertThat(series.current().size()).isEqualTo(24);
        assertThat(series.previous().size()).isEqualTo(24);

        // current: 0..23
        for (int i = 0; i < 24; i++) {
            assertThat(series.current().get(i)).isEqualTo(i);
        }
        // previous: 10..33
        for (int i = 0; i < 24; i++) {
            assertThat(series.previous().get(i)).isEqualTo(10 + i);
        }

        // 리포트 시각 차이(3시간)가 reportTimeGap 으로 반영되었는지 확인
        assertThat(series.reportTimeGap()).isEqualTo(3);
    }

    @Test
    void loadPopSeries_스냅하나라도없으면_null과_gap0반환() {
        Long regionId = 200L;
        Long curId = 1L;
        Long prvId = 10L;

        POPSnapDto onlyCur = new POPSnapDto();
        onlyCur.setSnapId(curId);
        onlyCur.setRegionId(regionId);
        onlyCur.setReportTime(LocalDateTime.now());
        onlyCur.setHourly(new PopSeries24(rangeList(0, 24)));

        // 이전 스냅이 없는 경우 (cur 만 반환)
        when(climateSnapRepository.findPopInfoBySnapIdsAndRegionId(List.of(curId, prvId), regionId))
                .thenReturn(List.of(onlyCur));

        ClimateService.PopSeries series = climateService.loadPopSeries(regionId, curId, prvId);

        assertThat(series.current()).isNull();
        assertThat(series.previous()).isNull();
        assertThat(series.reportTimeGap()).isEqualTo(0);
    }

    @Test
    void loadDefaultPopSeries_레포호출파라미터_검증() {
        // given
        Long regionId = 7L;
        Long curId = 1L;   // SNAP_CURRENT_DEFAULT
        Long prvId = 10L;  // SNAP_PREV_DEFAULT

        POPSnapDto cur = new POPSnapDto();
        cur.setSnapId(curId);
        cur.setRegionId(regionId);
        cur.setReportTime(LocalDateTime.now());
        cur.setHourly(new PopSeries24(rangeList(0, 24)));

        POPSnapDto prv = new POPSnapDto();
        prv.setSnapId(prvId);
        prv.setRegionId(regionId);
        prv.setReportTime(LocalDateTime.now().minusHours(3));
        prv.setHourly(new PopSeries24(rangeList(10, 24)));

        when(climateSnapRepository.findPopInfoBySnapIdsAndRegionId(List.of(curId, prvId), regionId))
                .thenReturn(List.of(cur, prv));

        // when
        climateService.loadDefaultPopSeries(regionId);

        // then
        verify(climateSnapRepository, times(1))
                .findPopInfoBySnapIdsAndRegionId(List.of(1L, 10L), regionId);
    }

    @Test
    void loadForecastSeries_hourly와_daily_매핑검증() {
        Long regionId = 55L;
        Long snapId = 1L;

        POPSnapDto popSnapDto = new POPSnapDto();
        popSnapDto.setSnapId(snapId);
        popSnapDto.setRegionId(regionId);
        popSnapDto.setReportTime(LocalDateTime.now());

        // hourly: 0..23
        popSnapDto.setHourly(new PopSeries24(rangeList(0, 24)));

        // daily: 7일치 AM/PM
        PopDailySeries7 daily = new PopDailySeries7(List.of(
                new PopDailySeries7.DailyPop(60, 10),
                new PopDailySeries7.DailyPop(10, 70),
                new PopDailySeries7.DailyPop(65, 65),
                new PopDailySeries7.DailyPop(0, 0),
                new PopDailySeries7.DailyPop(80, 0),
                new PopDailySeries7.DailyPop(0, 80),
                new PopDailySeries7.DailyPop(50, 50)
        ));
        popSnapDto.setDaily(daily);

        when(climateSnapRepository.findPopInfoBySnapIdsAndRegionId(List.of(snapId), regionId))
                .thenReturn(List.of(popSnapDto));

        // when
        ClimateService.ForecastSeries fs = climateService.loadForecastSeries(regionId, snapId);

        // then
        assertThat(fs.hourly()).isNotNull();
        assertThat(fs.daily()).isNotNull();

        // hourly 0..23
        assertThat(fs.hourly().size()).isEqualTo(24);
        for (int i = 0; i < 24; i++) {
            assertThat(fs.hourly().get(i)).isEqualTo(i);
        }

        // daily 7일 AM/PM
        assertThat(fs.daily().getDays()).hasSize(7);
        assertThat(fs.daily().getDays().get(0).getAm()).isEqualTo(60);
        assertThat(fs.daily().getDays().get(0).getPm()).isEqualTo(10);
        assertThat(fs.daily().getDays().get(1).getPm()).isEqualTo(70);
        assertThat(fs.daily().getDays().get(4).getAm()).isEqualTo(80);
        assertThat(fs.daily().getDays().get(5).getPm()).isEqualTo(80);
    }

    @Test
    void loadForecastSeries_행없으면_null들_반환() {
        Long regionId = 99L;
        Long snapId = 1L;

        when(climateSnapRepository.findPopInfoBySnapIdsAndRegionId(List.of(snapId), regionId))
                .thenReturn(List.of());   // 빈 리스트

        ClimateService.ForecastSeries fs = climateService.loadForecastSeries(regionId, snapId);

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
}