package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.notification.model.PopSeries24;
import com.github.yun531.climate.service.notification.model.PopSeriesPair;
import com.github.yun531.climate.service.notification.model.RainThresholdEnum;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static com.github.yun531.climate.util.time.TimeUtil.nowMinutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RainOnsetChangeRuleTest {

    /** RainOnsetChangeRule.RECOMPUTE_THRESHOLD_MINUTES 와 동일하게 유지 */
    private static final int TTL_MINUTES = 165;

    @Mock
    SnapshotQueryService snapshotQueryService;

    @Test
    void 이전은비아님_현재는비임_교차시각_validAt_에_AlertEvent_발생() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(snapshotQueryService);

        int th = RainThresholdEnum.RAIN.getThreshold(); // 60
        String regionId = "11B10101";

        // nowHour(+1~+24) 윈도우에 확실히 들어가도록, validAt을 nowHour 기준으로 생성
        LocalDateTime nowHour = nowMinutes().truncatedTo(ChronoUnit.HOURS);

        // A01=now+1 ... A26=now+26
        // onset을 "now+5"에서 발생시키기
        PopSeriesPair series = seriesWithOnsetAtValidAtHourOffset(nowHour, 5, th);

        when(snapshotQueryService.loadDefaultPopSeries(regionId)).thenReturn(series);

        // when
        NotificationRequest req = rainReq(regionId, null);
        var events = rule.evaluate(req);

        // then
        assertThat(events).hasSize(1);

        var e = events.get(0);
        assertThat(e.type()).isEqualTo(AlertTypeEnum.RAIN_ONSET);
        assertThat(e.regionId()).isEqualTo(regionId);

        // payload: validAt(ISO String), pop(int)
        Object validAtObj = e.payload().get("validAt");
        assertThat(validAtObj).isInstanceOf(String.class);
        assertThat((String) validAtObj).isEqualTo(nowHour.plusHours(5).toString());

        assertThat((Integer) e.payload().get("pop")).isEqualTo(th);

        // occurredAt은 반환 시점(nowHour 등)으로 보정될 수 있으므로 "현재 시각 이전/이하"만 보장
        assertThat(e.occurredAt()).isBeforeOrEqualTo(nowMinutes());
    }

    @Test
    void 이전부터_이미비임_교차없음_이벤트없음() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(snapshotQueryService);

        int th = RainThresholdEnum.RAIN.getThreshold();
        String regionId = "11B20201";

        LocalDateTime nowHour = nowMinutes().truncatedTo(ChronoUnit.HOURS);

        // 같은 validAt에서 이전도 이미 비(>=th), 현재도 비(>=th) -> onset 아님
        PopSeriesPair series = seriesAlreadyRainingAtValidAtHourOffset(nowHour, 5, th);

        when(snapshotQueryService.loadDefaultPopSeries(regionId)).thenReturn(series);

        NotificationRequest req = rainReq(regionId, null);
        var events = rule.evaluate(req);

        assertThat(events).isEmpty();
    }

    @Test
    void 시계열없음_null이면_스킵() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(snapshotQueryService);

        String regionId = "11B20601";
        LocalDateTime nowHour = nowMinutes().truncatedTo(ChronoUnit.HOURS);

        when(snapshotQueryService.loadDefaultPopSeries(regionId))
                .thenReturn(new PopSeriesPair(null, null, 0, nowHour));

        NotificationRequest req = rainReq(regionId, null);
        var events = rule.evaluate(req);

        assertThat(events).isEmpty();
    }

    @Test
    void 캐시_재사용_since가_있으면_두번째_호출시_재계산_안함() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(snapshotQueryService);

        String regionId = "11B30101";
        int th = RainThresholdEnum.RAIN.getThreshold();
        LocalDateTime nowHour = nowMinutes().truncatedTo(ChronoUnit.HOURS);

        PopSeriesPair series = seriesWithOnsetAtValidAtHourOffset(nowHour, 5, th);

        when(snapshotQueryService.loadDefaultPopSeries(regionId))
                .thenReturn(series); // 최초 1회만 호출되길 기대

        // 1) 최초 호출 → 캐시 생성 (since == null 이므로 무조건 계산)
        NotificationRequest firstReq = rainReq(regionId, null);
        var events1 = rule.evaluate(firstReq);
        assertThat(events1).hasSize(1);

        // 2) 두 번째 호출(since != null, TTL 이내) → 캐시 재사용
        // NOTE: 캐시 판단은 CacheEntry.computedAt(=curReportTime)에 의존하므로 그 값을 기준으로 since 구성
        LocalDateTime computedAt = series.curReportTime();
        NotificationRequest secondReq = rainReq(regionId, computedAt.plusMinutes(10));

        var events2 = rule.evaluate(secondReq);
        assertThat(events2).hasSize(1);

        verify(snapshotQueryService, times(1)).loadDefaultPopSeries(regionId);
    }

    @Test
    void invalidate_호출시_다음_호출에서_재계산() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(snapshotQueryService);

        String regionId = "11B40101";
        int th = RainThresholdEnum.RAIN.getThreshold();
        LocalDateTime nowHour = nowMinutes().truncatedTo(ChronoUnit.HOURS);

        when(snapshotQueryService.loadDefaultPopSeries(regionId))
                .thenReturn(seriesWithOnsetAtValidAtHourOffset(nowHour, 3, th));

        // 1) 최초 호출 → 계산 1회
        NotificationRequest req1 = rainReq(regionId, null);
        rule.evaluate(req1);
        verify(snapshotQueryService, times(1)).loadDefaultPopSeries(regionId);

        // 2) 캐시 무효화
        rule.invalidate(regionId);

        // 3) 다음 호출 → 다시 계산
        NotificationRequest req2 = rainReq(regionId, null);
        rule.evaluate(req2);
        verify(snapshotQueryService, times(2)).loadDefaultPopSeries(regionId);
    }

    @Test
    void since가_충분히_인접하면_재계산_안함() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(snapshotQueryService);

        String regionId = "11B50101";
        int th = RainThresholdEnum.RAIN.getThreshold();
        LocalDateTime nowHour = nowMinutes().truncatedTo(ChronoUnit.HOURS);

        PopSeriesPair series = seriesWithOnsetAtValidAtHourOffset(nowHour, 10, th);
        when(snapshotQueryService.loadDefaultPopSeries(regionId)).thenReturn(series);

        // 최초 계산
        NotificationRequest firstReq = rainReq(regionId, null);
        var events1 = rule.evaluate(firstReq);
        assertThat(events1).hasSize(1);
        verify(snapshotQueryService, times(1)).loadDefaultPopSeries(regionId);

        LocalDateTime baseComputedAt = series.curReportTime();

        // TTL - 10분 이내면 재계산 안 함
        LocalDateTime sinceNear = baseComputedAt.plusMinutes(TTL_MINUTES - 10);
        NotificationRequest nearReq = rainReq(regionId, sinceNear);
        rule.evaluate(nearReq);
        verify(snapshotQueryService, times(1)).loadDefaultPopSeries(regionId);

        // TTL + 10분 이후면 재계산
        LocalDateTime sinceFar = baseComputedAt.plusMinutes(TTL_MINUTES + 10);
        NotificationRequest farReq = rainReq(regionId, sinceFar);
        rule.evaluate(farReq);

        verify(snapshotQueryService, times(2)).loadDefaultPopSeries(regionId);
    }

    @Test
    void 경계값_TTL마이너스1분은_재계산안함_TTL플러스1분은_재계산() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(snapshotQueryService);

        String regionId = "11B60101";
        int th = RainThresholdEnum.RAIN.getThreshold();
        LocalDateTime nowHour = nowMinutes().truncatedTo(ChronoUnit.HOURS);

        PopSeriesPair series = seriesWithOnsetAtValidAtHourOffset(nowHour, 5, th);
        when(snapshotQueryService.loadDefaultPopSeries(regionId)).thenReturn(series);

        // 최초 계산
        NotificationRequest firstReq = rainReq(regionId, null);
        var events1 = rule.evaluate(firstReq);
        assertThat(events1).hasSize(1);
        verify(snapshotQueryService, times(1)).loadDefaultPopSeries(regionId);

        LocalDateTime baseComputedAt = series.curReportTime();

        // since = computedAt + (TTL - 1)분 → 재계산 안 함
        LocalDateTime sinceMinus1 = baseComputedAt.plusMinutes(TTL_MINUTES - 1);
        NotificationRequest minusReq = rainReq(regionId, sinceMinus1);
        rule.evaluate(minusReq);
        verify(snapshotQueryService, times(1)).loadDefaultPopSeries(regionId);

        // since = computedAt + (TTL + 1)분 → 재계산
        LocalDateTime sincePlus1 = baseComputedAt.plusMinutes(TTL_MINUTES + 1);
        NotificationRequest plusReq = rainReq(regionId, sincePlus1);
        rule.evaluate(plusReq);
        verify(snapshotQueryService, times(2)).loadDefaultPopSeries(regionId);
    }

    @Test
    void since를_미래로_크게_당기면_오래된_캐시로_판단되어_재계산() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(snapshotQueryService);

        String regionId = "11B70101";
        int th = RainThresholdEnum.RAIN.getThreshold();
        LocalDateTime nowHour = nowMinutes().truncatedTo(ChronoUnit.HOURS);

        PopSeriesPair series = seriesWithOnsetAtValidAtHourOffset(nowHour, 3, th);
        when(snapshotQueryService.loadDefaultPopSeries(regionId)).thenReturn(series);

        // 최초 계산
        NotificationRequest firstReq = rainReq(regionId, null);
        var events1 = rule.evaluate(firstReq);
        assertThat(events1).hasSize(1);
        verify(snapshotQueryService, times(1)).loadDefaultPopSeries(regionId);

        // TTL 보다 훨씬 미래 since → 재계산
        LocalDateTime computedAt = series.curReportTime();
        LocalDateTime sinceFuture = computedAt.plusMinutes(TTL_MINUTES + 60);
        NotificationRequest futureReq = rainReq(regionId, sinceFuture);
        rule.evaluate(futureReq);

        verify(snapshotQueryService, times(2)).loadDefaultPopSeries(regionId);
    }

    @Test
    void 지역별_캐시_키_분리_검증() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(snapshotQueryService);

        String regionId01 = "11B80101";
        String regionId02 = "11B80201";
        int th = RainThresholdEnum.RAIN.getThreshold();
        LocalDateTime nowHour = nowMinutes().truncatedTo(ChronoUnit.HOURS);

        PopSeriesPair series01 = seriesWithOnsetAtValidAtHourOffset(nowHour, 2, th);
        PopSeriesPair series02 = seriesWithOnsetAtValidAtHourOffset(nowHour, 6, th);

        when(snapshotQueryService.loadDefaultPopSeries(regionId01)).thenReturn(series01);
        when(snapshotQueryService.loadDefaultPopSeries(regionId02)).thenReturn(series02);

        // 1) 두 지역 동시에 호출 → 각 1회씩 계산
        NotificationRequest reqBoth = rainReq(List.of(regionId01, regionId02), null);
        rule.evaluate(reqBoth);
        verify(snapshotQueryService, times(1)).loadDefaultPopSeries(regionId01);
        verify(snapshotQueryService, times(1)).loadDefaultPopSeries(regionId02);

        // 2) r1만 다시 호출(since != null) → r1 캐시 재사용, r2는 건드리지 않음
        LocalDateTime since = series01.curReportTime().plusMinutes(10);
        NotificationRequest reqR1 = rainReq(regionId01, since);
        rule.evaluate(reqR1);
        verify(snapshotQueryService, times(1)).loadDefaultPopSeries(regionId01);
        verify(snapshotQueryService, times(1)).loadDefaultPopSeries(regionId02);
    }

    @Test
    void invalidateAll_호출시_모든_지역_재계산() {
        RainOnsetChangeRule rule = new RainOnsetChangeRule(snapshotQueryService);

        String regionId01 = "11B90101";
        String regionId02 = "11B90201";
        int th = RainThresholdEnum.RAIN.getThreshold();
        LocalDateTime nowHour = nowMinutes().truncatedTo(ChronoUnit.HOURS);

        when(snapshotQueryService.loadDefaultPopSeries(regionId01))
                .thenReturn(seriesWithOnsetAtValidAtHourOffset(nowHour, 1, th));
        when(snapshotQueryService.loadDefaultPopSeries(regionId02))
                .thenReturn(seriesWithOnsetAtValidAtHourOffset(nowHour, 4, th));

        // 최초 계산
        NotificationRequest firstReq = rainReq(List.of(regionId01, regionId02), null);
        rule.evaluate(firstReq);
        verify(snapshotQueryService, times(1)).loadDefaultPopSeries(regionId01);
        verify(snapshotQueryService, times(1)).loadDefaultPopSeries(regionId02);

        // 전체 무효화
        rule.invalidateAll();

        // 다시 호출 → 두 지역 모두 재계산
        NotificationRequest secondReq = rainReq(List.of(regionId01, regionId02), null);
        rule.evaluate(secondReq);
        verify(snapshotQueryService, times(2)).loadDefaultPopSeries(regionId01);
        verify(snapshotQueryService, times(2)).loadDefaultPopSeries(regionId02);
    }

    /* ===================== NotificationRequest helper ===================== */

    private NotificationRequest rainReq(String regionId, LocalDateTime since) {
        return rainReq(List.of(regionId), since);
    }

    private NotificationRequest rainReq(List<String> regionIds, LocalDateTime since) {
        // 이 룰은 enabledTypes / filterKinds / rainHourLimit 를 사용하지 않으므로 전부 null
        return new NotificationRequest(
                regionIds,
                since,
                null,
                null,
                null
        );
    }

    /* ===================== PopSeriesPair helper ===================== */

    /**
     * nowHour 기준으로 A01=now+1 ... A26=now+26 validAt을 생성하고,
     * 특정 hourOffset(1..26)에서만 "이전<th, 현재>=th" 교차를 만든다.
     *
     * curReportTime은 캐시 기준시각이므로 nowHour로 맞춰 일관성 유지.
     */
    private static PopSeriesPair seriesWithOnsetAtValidAtHourOffset(LocalDateTime nowHour, int hourOffset, int th) {
        List<PopSeries24.Point> cur = new ArrayList<>(PopSeries24.SIZE);
        List<PopSeries24.Point> prv = new ArrayList<>(PopSeries24.SIZE);

        for (int i = 1; i <= PopSeries24.SIZE; i++) {
            LocalDateTime at = nowHour.plusHours(i);

            int curPop = 0;
            int prvPop = 0;

            if (i == hourOffset) {
                curPop = th;
                prvPop = th - 1;
            }

            cur.add(new PopSeries24.Point(at, curPop));
            prv.add(new PopSeries24.Point(at, prvPop));
        }

        return new PopSeriesPair(
                new PopSeries24(cur),
                new PopSeries24(prv),
                0,
                nowHour
        );
    }

    /**
     * 특정 validAt에서 이전도 이미 비(>=th), 현재도 비(>=th) -> onset 없음
     */
    private static PopSeriesPair seriesAlreadyRainingAtValidAtHourOffset(LocalDateTime nowHour, int hourOffset, int th) {
        List<PopSeries24.Point> cur = new ArrayList<>(PopSeries24.SIZE);
        List<PopSeries24.Point> prv = new ArrayList<>(PopSeries24.SIZE);

        for (int i = 1; i <= PopSeries24.SIZE; i++) {
            LocalDateTime at = nowHour.plusHours(i);

            int curPop = 0;
            int prvPop = 0;

            if (i == hourOffset) {
                curPop = th + 10;
                prvPop = th + 1;
            }

            cur.add(new PopSeries24.Point(at, curPop));
            prv.add(new PopSeries24.Point(at, prvPop));
        }

        return new PopSeriesPair(
                new PopSeries24(cur),
                new PopSeries24(prv),
                0,
                nowHour
        );
    }
}