package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.PopView;
import com.github.yun531.climate.service.notification.model.payload.RainForecastPayload;
import com.github.yun531.climate.service.notification.rule.adjust.RainForecastPartsAdjuster;
import com.github.yun531.climate.service.notification.rule.compute.RainForecastComputer;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import com.github.yun531.climate.service.snapshot.model.SnapKindEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RainForecastRuleDayPartsTest {

    @Mock
    SnapshotQueryService snapshotQueryService;

    @Test
    void dayParts_ampm7x2_플래그_생성검증() {
        LocalDateTime now = LocalDateTime.parse("2025-11-18T08:00:00");

        int thresholdPop = 60;
        int maxPoints = 26;        // hourly 최대 포인트
        int maxShiftHours = 2;     // 0/1/2 재사용
        int windowHours = 24;      // horizon
        int recomputeThresholdMinutes = 165;

        RainForecastComputer computer = new RainForecastComputer(thresholdPop, maxPoints);
        RainForecastPartsAdjuster adjuster = new RainForecastPartsAdjuster(maxShiftHours, windowHours);

        RainForecastRule rule = new RainForecastRule(
                snapshotQueryService,
                computer,
                adjuster,
                recomputeThresholdMinutes,
                thresholdPop
        );

        PopView.DailyPopSeries7 daily = new PopView.DailyPopSeries7(List.of(
                new PopView.DailyPopSeries7.DailyPop(70, 10),
                new PopView.DailyPopSeries7.DailyPop(10, 70),
                new PopView.DailyPopSeries7.DailyPop(65, 65),
                new PopView.DailyPopSeries7.DailyPop(0, 0),
                new PopView.DailyPopSeries7.DailyPop(80, 0),
                new PopView.DailyPopSeries7.DailyPop(0, 0),
                new PopView.DailyPopSeries7.DailyPop(0, 0)
        ));

        int snapId = SnapKindEnum.SNAP_CURRENT.getCode();
        String regionId = "11B10101";

        // hourly는 이번 테스트의 핵심이 아니라 pop=0으로 채움
        LocalDateTime base = now.plusHours(5);
        PopView.HourlyPopSeries26 hourly = buildHourlySeries26(base, 0);

        // reportTime을 now로 맞춰서 shift/dayShift 영향 제거
        PopView pop = new PopView(hourly, daily, now);

        when(snapshotQueryService.loadPopView(regionId, snapId)).thenReturn(pop);

        NotificationRequest request = new NotificationRequest(
                List.of(regionId),
                now,   // since
                null,  // enabledTypes (서비스에서 거르는 값)
                null,  // filterWarningKinds
                null   // rainHourLimit
        );

        // when: now 주입(새 구조)
        var events = rule.evaluate(request, now);

        // then
        assertThat(events).hasSize(1);

        var payload = (RainForecastPayload) events.get(0).payload();

        List<List<Integer>> dayParts =
                payload.dayParts().stream()
                        .map(f -> List.of(f.rainAm() ? 1 : 0, f.rainPm() ? 1 : 0))
                        .toList();

        assertThat(dayParts).containsExactly(
                List.of(1, 0),
                List.of(0, 1),
                List.of(1, 1),
                List.of(0, 0),
                List.of(1, 0),
                List.of(0, 0),
                List.of(0, 0)
        );
    }

    private static PopView.HourlyPopSeries26 buildHourlySeries26(LocalDateTime base, int popAll) {
        List<PopView.HourlyPopSeries26.Point> points = new ArrayList<>(PopView.HOURLY_SIZE);
        for (int i = 1; i <= PopView.HOURLY_SIZE; i++) {
            points.add(new PopView.HourlyPopSeries26.Point(base.plusHours(i), popAll));
        }
        return new PopView.HourlyPopSeries26(points);
    }
}