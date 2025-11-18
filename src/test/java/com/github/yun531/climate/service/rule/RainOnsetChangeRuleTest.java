package com.github.yun531.climate.service.rule;


import com.github.yun531.climate.domain.PopSeries24;
import com.github.yun531.climate.service.ClimateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RainOnsetChangeRuleTest {

    /** RainOnsetChangeRule.RECOMPUTE_THRESHOLD_MINUTES 와 동일하게 유지 */
    private static final long TTL_MINUTES = 165L;

    @Mock
    ClimateService climateService;

    @Test
    void 이전은비아님_현재는비임_교차시각에_AlertEvent_발생() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);

        int th = RainThresholdEnum.RAIN.getThreshold(); // 60

        when(climateService.loadDefaultPopSeries(101L))
                .thenReturn(seriesWithCrossAtHour(5, th));

        // when
        var events = rule.evaluate(List.of(101L), null);

        // then
        assertThat(events).hasSize(1);

        var e = events.get(0);
        assertThat(e.type()).isEqualTo(AlertTypeEnum.RAIN_ONSET);
        assertThat(e.regionId()).isEqualTo(101L);
        assertThat((Integer) e.payload().get("hour")).isEqualTo(5);
        assertThat((Integer) e.payload().get("pop")).isEqualTo(th);
        assertThat(e.occurredAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void 이전부터_이미비임_교차없음_이벤트없음() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);

        int th = RainThresholdEnum.RAIN.getThreshold();

        List<Integer> curVals = new ArrayList<>(Collections.nCopies(24, 0));
        List<Integer> prvVals = new ArrayList<>(Collections.nCopies(24, 0));
        // h=5 에서 둘 다 비(임계치 이상)
        curVals.set(5, th + 10);
        prvVals.set(8, th + 1);

        PopSeries24 current = new PopSeries24(curVals);
        PopSeries24 previous = new PopSeries24(prvVals);

        when(climateService.loadDefaultPopSeries(7L))
                .thenReturn(new ClimateService.PopSeries(current, previous, 3));

        var events = rule.evaluate(List.of(7L), null);
        assertThat(events).isEmpty();
    }

    @Test
    void 시계열없음_null이면_스킵() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);

        when(climateService.loadDefaultPopSeries(9L))
                .thenReturn(new ClimateService.PopSeries(null, null, 0));

        var events = rule.evaluate(List.of(9L), null);
        assertThat(events).isEmpty();
    }

    @Test
    void 캐시_재사용_since가_있으면_두번째_호출시_재계산_안함() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        long region = 42L;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(region))
                .thenReturn(seriesWithCrossAtHour(5, th)); // 최초 1회만 호출되길 기대

        // 1) 최초 호출 → 캐시 생성 (since == null 이므로 무조건 계산)
        var events1 = rule.evaluate(List.of(region), null);

        assertThat(events1).hasSize(1);

        Instant computedAt = events1.get(0).occurredAt();

        // 2) 두 번째 호출(since != null, TTL 이내) → 캐시 재사용
        var events2 = rule.evaluate(List.of(region), computedAt.plus(10, ChronoUnit.MINUTES));

        assertThat(events2).hasSize(1);

        // 총 1번 호출되었는지 확인
        verify(climateService, times(1)).loadDefaultPopSeries(region);
    }

    @Test
    void invalidate_호출시_다음_호출에서_재계산() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        long region = 77L;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(region))
                .thenReturn(seriesWithCrossAtHour(3, th));

        // 1) 최초 호출 → 계산 1회
        rule.evaluate(List.of(region), null);
        verify(climateService, times(1)).loadDefaultPopSeries(region);

        // 2) 캐시 무효화
        rule.invalidate(region);

        // 3) 다음 호출 → 다시 계산
        rule.evaluate(List.of(region), null);
        verify(climateService, times(2)).loadDefaultPopSeries(region);
    }

    @Test
    void since가_충분히_인접하면_재계산_안함() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        long region = 200L;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(region))
                .thenReturn(seriesWithCrossAtHour(10, th));

        // 최초 계산
        var events1 = rule.evaluate(List.of(region), null);
        assertThat(events1).hasSize(1);
        Instant computedAt = events1.get(0).occurredAt();
        verify(climateService, times(1)).loadDefaultPopSeries(region);

        // TTL - 10분 이내면 재계산 안 함
        Instant sinceNear = computedAt.plus(TTL_MINUTES - 10, ChronoUnit.MINUTES);
        rule.evaluate(List.of(region), sinceNear);
        verify(climateService, times(1)).loadDefaultPopSeries(region);

        // TTL + 10분 이후면 재계산
        Instant sinceFar = computedAt.plus(TTL_MINUTES + 10, ChronoUnit.MINUTES);
        rule.evaluate(List.of(region), sinceFar);

        // 총 2회 호출 확인
        verify(climateService, times(2)).loadDefaultPopSeries(region);
    }

    @Test
    void 경계값_TTL마이너스1분은_재계산안함_TTL플러스1분은_재계산() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        long region = 120L;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(region))
                .thenReturn(seriesWithCrossAtHour(5, th));

        // 최초 계산
        var events1 = rule.evaluate(List.of(region), null);
        assertThat(events1).hasSize(1);
        Instant computedAt = events1.get(0).occurredAt();
        verify(climateService, times(1)).loadDefaultPopSeries(region);

        // since = computedAt + (TTL - 1)분 → threshold = since - TTL = computedAt - 1분
        // computedAt 이 threshold 이전이 아니므로 재계산 안 함
        Instant sinceMinus1 = computedAt.plus(TTL_MINUTES - 1, ChronoUnit.MINUTES);
        rule.evaluate(List.of(region), sinceMinus1);
        verify(climateService, times(1)).loadDefaultPopSeries(region);

        // since = computedAt + (TTL + 1)분 → threshold = computedAt + 1분
        // computedAt 이 threshold 이전이므로 재계산
        Instant sincePlus1 = computedAt.plus(TTL_MINUTES + 1, ChronoUnit.MINUTES);
        rule.evaluate(List.of(region), sincePlus1);
        verify(climateService, times(2)).loadDefaultPopSeries(region);
    }

    @Test
    void since를_미래로_크게_당기면_오래된_캐시로_판단되어_재계산() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        long region = 130L;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(region))
                .thenReturn(seriesWithCrossAtHour(3, th));

        // 최초 계산
        var events1 = rule.evaluate(List.of(region), null);
        assertThat(events1).hasSize(1);
        Instant computedAt = events1.get(0).occurredAt();
        verify(climateService, times(1)).loadDefaultPopSeries(region);

        // TTL 보다 훨씬 미래 since → 재계산
        Instant sinceFuture = computedAt.plus(TTL_MINUTES + 60, ChronoUnit.MINUTES);
        rule.evaluate(List.of(region), sinceFuture);
        verify(climateService, times(2)).loadDefaultPopSeries(region);
    }

    @Test
    void 지역별_캐시_키_분리_검증() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        long r1 = 1L, r2 = 2L;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(r1))
                .thenReturn(seriesWithCrossAtHour(2, th));
        when(climateService.loadDefaultPopSeries(r2))
                .thenReturn(seriesWithCrossAtHour(6, th));

        // 1) 두 지역 동시에 호출 → 각 1회씩 계산
        rule.evaluate(List.of(r1, r2), null);
        verify(climateService, times(1)).loadDefaultPopSeries(r1);
        verify(climateService, times(1)).loadDefaultPopSeries(r2);

        // 2) r1만 다시 호출(since != null) → r1 캐시 재사용, r2는 건드리지 않음
        rule.evaluate(List.of(r1), Instant.now());
        verify(climateService, times(1)).loadDefaultPopSeries(r1);
        verify(climateService, times(1)).loadDefaultPopSeries(r2);
    }

    @Test
    void invalidateAll_호출시_모든_지역_재계산() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        long r1 = 10L, r2 = 20L;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(r1))
                .thenReturn(seriesWithCrossAtHour(1, th));
        when(climateService.loadDefaultPopSeries(r2))
                .thenReturn(seriesWithCrossAtHour(4, th));

        // 최초 계산
        rule.evaluate(List.of(r1, r2), null);
        verify(climateService, times(1)).loadDefaultPopSeries(r1);
        verify(climateService, times(1)).loadDefaultPopSeries(r2);

        // 전체 무효화
        rule.invalidateAll();

        // 다시 호출 → 두 지역 모두 재계산
        rule.evaluate(List.of(r1, r2), null);
        verify(climateService, times(2)).loadDefaultPopSeries(r1);
        verify(climateService, times(2)).loadDefaultPopSeries(r2);
    }

    // ---- helper ----
    private static ClimateService.PopSeries seriesWithCrossAtHour(int hour, int th) {
        List<Integer> curVals = new ArrayList<>(Collections.nCopies(24, 0));
        List<Integer> prvVals = new ArrayList<>(Collections.nCopies(24, 0));

        // 해당 시각에서만 "비가 되었다" 상황
        curVals.set(hour, th);        // now ≥ th
        prvVals.set(hour, th - 1);    // was < th

        PopSeries24 current = new PopSeries24(curVals);
        PopSeries24 previous = new PopSeries24(prvVals);

        // 이전 스냅과 현재 스냅의 시간 간격(시간 단위) – 테스트에서는 0으로 단순화
        int gapHours = 0;
        return new ClimateService.PopSeries(current, previous, gapHours);
    }
}