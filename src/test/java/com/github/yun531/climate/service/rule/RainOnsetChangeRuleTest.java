package com.github.yun531.climate.service.rule;


import com.github.yun531.climate.service.ClimateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RainOnsetChangeRuleTest {

    @Mock
    ClimateService climateService;

    @Test
    void 이전은비아님_현재는비임_교차시각에_AlertEvent_발생() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);

        int th = RainThresholdEnum.RAIN.getThreshold(); // 60
        // h=5에서 임계치 교차
        int[] current = new int[24];
        int[] prevShift = new int[23];
        for (int i = 0; i < 24; i++) current[i] = 0;
        for (int i = 0; i < 23; i++) prevShift[i] = 0;
        current[5] = th;                 // now ≥ 60
        prevShift[5] = th - 1;          // was < 60

        when(climateService.loadDefaultPopSeries(101L))
                .thenReturn(new ClimateService.PopSeries(current, prevShift));

        // when
        var events = rule.evaluate(List.of(101L), null);
        // then
        assertThat(events).hasSize(1);

        // when
        var e = events.get(0);
        // then
        assertThat(e.type()).isEqualTo(AlertTypeEnum.RAIN_ONSET);
        assertThat(e.regionId()).isEqualTo(101L);
        assertThat((Integer)e.payload().get("hour")).isEqualTo(5);
        assertThat((Integer)e.payload().get("pop")).isEqualTo(th);

        assertThat(e.occurredAt()).isBeforeOrEqualTo(Instant.now());         // todo: since 관련 고민중
    }

    @Test
    void 이전부터_이미비임_교차없음_이벤트없음() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);

        int th = RainThresholdEnum.RAIN.getThreshold();
        int[] current = new int[24];
        int[] prevShift = new int[23];
        current[5] = th + 10;
        prevShift[5] = th + 1; // 이미 비

        when(climateService.loadDefaultPopSeries(7L))
                .thenReturn(new ClimateService.PopSeries(current, prevShift));

        // when
        var events = rule.evaluate(List.of(7L), null);
        // then
        assertThat(events).isEmpty();
    }


    @Test
    void 시계열없음_null이면_스킵() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);

        when(climateService.loadDefaultPopSeries(9L))
                .thenReturn(new ClimateService.PopSeries(null, null));

        var events = rule.evaluate(List.of(9L), null);
        assertThat(events).isEmpty();
    }

    @Test
    void 캐시_재사용_since_null이면_두번째_호출시_재계산_안함() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        long region = 42L;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(region))
                .thenReturn(seriesWithCrossAtHour(5, th)); // 최초 1회만 호출되길 기대

        // 1) 최초 호출 → 캐시 생성  (캐시 비어있으면 무조건 계산)
        var events1 = rule.evaluate(List.of(region), null);
        assertThat(events1).hasSize(1);

        // 2) 두번째 호출(since!=null) → 캐시 재사용, 재계산 없음   (캐시 존재하고, since != null 이면 계산 X)
        var events2 = rule.evaluate(List.of(region), Instant.now());
        assertThat(events2).hasSize(1);

        verify(climateService, times(1)).loadDefaultPopSeries(region);
    }

    @Test
    void invalidate_호출시_다음_호출에서_재계산() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        long region = 77L;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(region))
                .thenReturn(seriesWithCrossAtHour(3, th));

        // 1) 최초 호출 → 캐시 생성
        rule.evaluate(List.of(region), null);
        verify(climateService, times(1)).loadDefaultPopSeries(region);

        // 2) 캐시 무효화
        rule.invalidate(region);

        // 3) 다음 호출 → 재계산 발생
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

        rule.evaluate(List.of(region), null);
        verify(climateService, times(1)).loadDefaultPopSeries(region);

        // 2) since=now → 40분 이내이므로 재계산 안 함
        rule.evaluate(List.of(region), Instant.now());
        verify(climateService, times(1)).loadDefaultPopSeries(region);

        // 3) since를 computedAt보다 40분+α 뒤로 밀면 → 재계산
        rule.evaluate(List.of(region), Instant.now().plus(41, ChronoUnit.MINUTES));
        verify(climateService, times(2)).loadDefaultPopSeries(region);
    }

    @Test
    void 경계값_39분은_재계산안함_41분은_재계산() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        long region = 120L;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(region))
                .thenReturn(seriesWithCrossAtHour(5, th));

        // 최초 호출 → 캐시 생성 (computedAt ≈ now)
        rule.evaluate(List.of(region), null);
        verify(climateService, times(1)).loadDefaultPopSeries(region);

        // since = now + 39분 → threshold = since - 40분 ≈ now - 1분
        // computedAt(≈ now) 가 threshold(≈ now-1분) 보다 이전이 아님 → 재계산 안함
        Instant sincePlus39 = Instant.now().plus(39, java.time.temporal.ChronoUnit.MINUTES);
        rule.evaluate(List.of(region), sincePlus39);
        verify(climateService, times(1)).loadDefaultPopSeries(region);

        // since = now + 41분 → threshold = since - 40분 ≈ now + 1분
        // computedAt(≈ now) 가 threshold(≈ now+1분) 보다 이전 → 재계산
        Instant sincePlus41 = Instant.now().plus(41, java.time.temporal.ChronoUnit.MINUTES);
        rule.evaluate(List.of(region), sincePlus41);
        verify(climateService, times(2)).loadDefaultPopSeries(region);
    }

    @Test
    void since를_미래로_크게_당기면_오래된_캐시로_판단되어_재계산() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        long region = 130L;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(region))
                .thenReturn(seriesWithCrossAtHour(3, th));

        // 최초 호출 → 캐시 생성
        rule.evaluate(List.of(region), null);
        verify(climateService, times(1)).loadDefaultPopSeries(region);

        // since = now + 2시간 → threshold = since - 40분 = now + 80분
        // computedAt(≈ now) 가 threshold(≈ now+80분) 보다 이전 → 재계산
        Instant sinceFuture = Instant.now().plus(2, java.time.temporal.ChronoUnit.HOURS);
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

        // 1) 두 지역 최초 호출 → 각 1회씩 계산
        rule.evaluate(List.of(r1, r2), null);
        verify(climateService, times(1)).loadDefaultPopSeries(r1);
        verify(climateService, times(1)).loadDefaultPopSeries(r2);

        // 2) r1만 다시 호출(since=null) → r1은 캐시, r2는 호출 안 함
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
        int[] current = new int[24];
        int[] prevShift = new int[23];
        // 기본은 비 아님
        for (int i = 0; i < 24; i++) current[i] = 0;
        for (int i = 0; i < 23; i++) prevShift[i] = 0;
        // hour에서 임계치 교차
        current[hour] = th;        // now >= th
        if (hour < 23) prevShift[hour] = th - 1; // was < th
        return new ClimateService.PopSeries(current, prevShift);
    }
}