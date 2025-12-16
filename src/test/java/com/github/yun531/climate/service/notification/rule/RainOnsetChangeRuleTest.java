package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.dto.PopSeriesPair;
import com.github.yun531.climate.dto.PopSeries24;
import com.github.yun531.climate.service.ClimateService;
import com.github.yun531.climate.service.notification.NotificationRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.github.yun531.climate.util.TimeUtil.nowMinutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RainOnsetChangeRuleTest {

    /** RainOnsetChangeRule.RECOMPUTE_THRESHOLD_MINUTES 와 동일하게 유지 */
    private static final int TTL_MINUTES = 165;

    @Mock
    ClimateService climateService;

    @Test
    void 이전은비아님_현재는비임_교차시각에_AlertEvent_발생() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);

        int th = RainThresholdEnum.RAIN.getThreshold(); // 60

        when(climateService.loadDefaultPopSeries(101))
                .thenReturn(seriesWithCrossAtHour(5, th));

        // when
        NotificationRequest req = rainReq(101, null);
        var events = rule.evaluate(req);

        // then
        assertThat(events).hasSize(1);

        var e = events.get(0);
        assertThat(e.type()).isEqualTo(AlertTypeEnum.RAIN_ONSET);
        assertThat(e.regionId()).isEqualTo(101L);
        assertThat((Integer) e.payload().get("hourOffset")).isEqualTo(5);
        assertThat((Integer) e.payload().get("pop")).isEqualTo(th);
        assertThat(e.occurredAt()).isBeforeOrEqualTo(nowMinutes());
    }

    @Test
    void 이전부터_이미비임_교차없음_이벤트없음() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);

        int th = RainThresholdEnum.RAIN.getThreshold();

        List<Integer> curVals = new ArrayList<>(Collections.nCopies(24, 0));
        List<Integer> prvVals = new ArrayList<>(Collections.nCopies(24, 0));
        // h=5 에서 둘 다 비(임계치 이상)
        curVals.set(5 -1, th + 10);
        prvVals.set(8 -1, th + 1);

        PopSeries24 current = new PopSeries24(curVals);
        PopSeries24 previous = new PopSeries24(prvVals);

        when(climateService.loadDefaultPopSeries(7))
                .thenReturn(new PopSeriesPair(
                        current,
                        previous,
                        3,
                        LocalDateTime.parse("2025-11-18T11:00:00"))
                );

        NotificationRequest req = rainReq(7, null);
        var events = rule.evaluate(req);

        assertThat(events).isEmpty();
    }

    @Test
    void 시계열없음_null이면_스킵() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);

        when(climateService.loadDefaultPopSeries(9))
                .thenReturn(new PopSeriesPair(
                        null, null, 0,
                        LocalDateTime.parse("2025-11-18T11:00:00"))
                );

        NotificationRequest req = rainReq(9, null);
        var events = rule.evaluate(req);

        assertThat(events).isEmpty();
    }

    @Test
    void 캐시_재사용_since가_있으면_두번째_호출시_재계산_안함() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        int regionId = 42;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(regionId))
                .thenReturn(seriesWithCrossAtHour(5, th)); // 최초 1회만 호출되길 기대

        // 1) 최초 호출 → 캐시 생성 (since == null 이므로 무조건 계산)
        NotificationRequest firstReq = rainReq(regionId, null);
        var events1 = rule.evaluate(firstReq);

        assertThat(events1).hasSize(1);
        LocalDateTime computedAt = events1.get(0).occurredAt();

        // 2) 두 번째 호출(since != null, TTL 이내) → 캐시 재사용
        NotificationRequest secondReq = rainReq(regionId, computedAt.plusMinutes(10));
        var events2 = rule.evaluate(secondReq);

        assertThat(events2).hasSize(1);
        verify(climateService, times(1)).loadDefaultPopSeries(regionId);
    }

    @Test
    void invalidate_호출시_다음_호출에서_재계산() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        int regionId = 77;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(regionId))
                .thenReturn(seriesWithCrossAtHour(3, th));

        // 1) 최초 호출 → 계산 1회
        NotificationRequest req1 = rainReq(regionId, null);
        rule.evaluate(req1);
        verify(climateService, times(1)).loadDefaultPopSeries(regionId);

        // 2) 캐시 무효화
        rule.invalidate(regionId);

        // 3) 다음 호출 → 다시 계산
        NotificationRequest req2 = rainReq(regionId, null);
        rule.evaluate(req2);
        verify(climateService, times(2)).loadDefaultPopSeries(regionId);
    }

    @Test
    void since가_충분히_인접하면_재계산_안함() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        int regionId = 200;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(regionId))
                .thenReturn(seriesWithCrossAtHour(10, th));

        // 최초 계산
        NotificationRequest firstReq = rainReq(regionId, null);
        var events1 = rule.evaluate(firstReq);
        assertThat(events1).hasSize(1);
        LocalDateTime computedAt = events1.get(0).occurredAt();
        verify(climateService, times(1)).loadDefaultPopSeries(regionId);

        // TTL - 10분 이내면 재계산 안 함
        LocalDateTime sinceNear = computedAt.plusMinutes(TTL_MINUTES - 10);
        NotificationRequest nearReq = rainReq(regionId, sinceNear);
        rule.evaluate(nearReq);
        verify(climateService, times(1)).loadDefaultPopSeries(regionId);

        // TTL + 10분 이후면 재계산
        LocalDateTime sinceFar = computedAt.plusMinutes(TTL_MINUTES + 10);
        NotificationRequest farReq = rainReq(regionId, sinceFar);
        rule.evaluate(farReq);

        // 총 2회 호출 확인
        verify(climateService, times(2)).loadDefaultPopSeries(regionId);
    }

    @Test
    void 경계값_TTL마이너스1분은_재계산안함_TTL플러스1분은_재계산() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        int regionId = 120;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(regionId))
                .thenReturn(seriesWithCrossAtHour(5, th));

        // 최초 계산
        NotificationRequest firstReq = rainReq(regionId, null);
        var events1 = rule.evaluate(firstReq);
        assertThat(events1).hasSize(1);
        LocalDateTime computedAt = events1.get(0).occurredAt();
        verify(climateService, times(1)).loadDefaultPopSeries(regionId);

        // since = computedAt + (TTL - 1)분 → threshold = since - TTL = computedAt - 1분
        // computedAt 이 threshold 이전이 아니므로 재계산 안 함
        LocalDateTime sinceMinus1 = computedAt.plusMinutes(TTL_MINUTES - 1);
        NotificationRequest minusReq = rainReq(regionId, sinceMinus1);
        rule.evaluate(minusReq);
        verify(climateService, times(1)).loadDefaultPopSeries(regionId);

        // since = computedAt + (TTL + 1)분 → threshold = computedAt + 1분
        // computedAt 이 threshold 이전이므로 재계산
        LocalDateTime sincePlus1 = computedAt.plusMinutes(TTL_MINUTES + 1);
        NotificationRequest plusReq = rainReq(regionId, sincePlus1);
        rule.evaluate(plusReq);
        verify(climateService, times(2)).loadDefaultPopSeries(regionId);
    }

    @Test
    void since를_미래로_크게_당기면_오래된_캐시로_판단되어_재계산() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        int regionId = 130;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(regionId))
                .thenReturn(seriesWithCrossAtHour(3, th));

        // 최초 계산
        NotificationRequest firstReq = rainReq(regionId, null);
        var events1 = rule.evaluate(firstReq);
        assertThat(events1).hasSize(1);
        LocalDateTime computedAt = events1.get(0).occurredAt();
        verify(climateService, times(1)).loadDefaultPopSeries(regionId);

        // TTL 보다 훨씬 미래 since → 재계산
        LocalDateTime sinceFuture = computedAt.plusMinutes(TTL_MINUTES + 60);
        NotificationRequest futureReq = rainReq(regionId, sinceFuture);
        rule.evaluate(futureReq);
        verify(climateService, times(2)).loadDefaultPopSeries(regionId);
    }

    @Test
    void 지역별_캐시_키_분리_검증() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        int regionId01 = 1, regionId02 = 2;
        int th = RainThresholdEnum.RAIN.getThreshold();

        PopSeriesPair series01 = seriesWithCrossAtHour(2, th);
        PopSeriesPair series02 = seriesWithCrossAtHour(6, th);

        when(climateService.loadDefaultPopSeries(regionId01)).thenReturn(series01);
        when(climateService.loadDefaultPopSeries(regionId02)).thenReturn(series02);

        // 1) 두 지역 동시에 호출 → 각 1회씩 계산
        NotificationRequest reqBoth = rainReq(List.of(regionId01, regionId02), null);
        rule.evaluate(reqBoth);
        verify(climateService, times(1)).loadDefaultPopSeries(regionId01);
        verify(climateService, times(1)).loadDefaultPopSeries(regionId02);

        // 2) r1만 다시 호출(since != null) → r1 캐시 재사용, r2는 건드리지 않음
        LocalDateTime since = series01.curReportTime().plusMinutes(10);
        NotificationRequest reqR1 = rainReq(regionId01, since);
        rule.evaluate(reqR1);
        verify(climateService, times(1)).loadDefaultPopSeries(regionId01);
        verify(climateService, times(1)).loadDefaultPopSeries(regionId02);
    }

    @Test
    void invalidateAll_호출시_모든_지역_재계산() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(climateService);
        int regionId01 = 10, regionId02 = 20;
        int th = RainThresholdEnum.RAIN.getThreshold();

        when(climateService.loadDefaultPopSeries(regionId01))
                .thenReturn(seriesWithCrossAtHour(1, th));
        when(climateService.loadDefaultPopSeries(regionId02))
                .thenReturn(seriesWithCrossAtHour(4, th));

        // 최초 계산
        NotificationRequest firstReq = rainReq(List.of(regionId01, regionId02), null);
        rule.evaluate(firstReq);
        verify(climateService, times(1)).loadDefaultPopSeries(regionId01);
        verify(climateService, times(1)).loadDefaultPopSeries(regionId02);

        // 전체 무효화
        rule.invalidateAll();

        // 다시 호출 → 두 지역 모두 재계산
        NotificationRequest secondReq = rainReq(List.of(regionId01, regionId02), null);
        rule.evaluate(secondReq);
        verify(climateService, times(2)).loadDefaultPopSeries(regionId01);
        verify(climateService, times(2)).loadDefaultPopSeries(regionId02);
    }


    /** NotificationRequest helper */

    private NotificationRequest rainReq(int regionId, LocalDateTime since) {
        return rainReq(List.of(regionId), since);
    }

    private NotificationRequest rainReq(List<Integer> regionIds, LocalDateTime since) {
        // 이 룰은 enabledTypes / filterKinds / rainHourLimit 를 사용하지 않으므로 전부 null
        return new NotificationRequest(
                regionIds,
                since,
                null,
                null,
                null
        );
    }

    /** PopSeries helper */
    private static PopSeriesPair seriesWithCrossAtHour(int hourOffset, int th) {
        List<Integer> curVals = new ArrayList<>(Collections.nCopies(24, 0));
        List<Integer> prvVals = new ArrayList<>(Collections.nCopies(24, 0));

        /** 해당 시각에서만 "비가 되었다" 상황
         *  offset(1..24) -> index(0..23) */
        curVals.set(hourOffset - 1, th);
        prvVals.set(hourOffset - 1, th - 1);

        PopSeries24 current = new PopSeries24(curVals);
        PopSeries24 previous = new PopSeries24(prvVals);

        int gapHours = 0;
        return new PopSeriesPair(
                current,
                previous,
                gapHours,
                LocalDateTime.parse("2025-11-18T11:00:00")
        );
    }
}