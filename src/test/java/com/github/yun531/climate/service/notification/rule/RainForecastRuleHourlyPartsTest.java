package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.notification.domain.rule.RainForecastRule;
import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload;
import com.github.yun531.climate.notification.domain.payload.RainInterval;
import com.github.yun531.climate.notification.domain.rule.adjust.RainForecastPartsAdjuster;
import com.github.yun531.climate.notification.domain.rule.compute.RainForecastComputer;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import com.github.yun531.climate.kernel.snapshot.SnapKind;
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
class RainForecastRuleHourlyPartsTest {

    @Mock
    SnapshotQueryService snapshotQueryService;

    @Test
    void hourlyParts_연속구간_start_end_validAt_을_반환한다() {
        // given: 테스트 결정성을 위해 now 고정
        LocalDateTime now = LocalDateTime.parse("2025-11-18T08:00:00");

        SnapKind curKind = SnapKind.CURRENT;
        String regionId = "11B10101";

        int thresholdPop = 60;
        int maxPoints = 26;
        int maxShiftHours = 2;
        int windowHours = 24;
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

        LocalDateTime base = now.plusHours(5);

        List<PopView.HourlyPopSeries26.Point> points = new ArrayList<>(PopView.HOURLY_SIZE);
        for (int i = 1; i <= PopView.HOURLY_SIZE; i++) {
            LocalDateTime validAt = base.plusHours(i);
            int pop = (i == 3 || i == 4 || i == 5) ? 60 : 0;
            points.add(new PopView.HourlyPopSeries26.Point(validAt, pop));
        }
        PopView.HourlyPopSeries26 hourly = new PopView.HourlyPopSeries26(points);

        PopView.DailyPopSeries7 daily = new PopView.DailyPopSeries7(List.of(
                new PopView.DailyPopSeries7.DailyPop(0, 0),
                new PopView.DailyPopSeries7.DailyPop(0, 0),
                new PopView.DailyPopSeries7.DailyPop(0, 0),
                new PopView.DailyPopSeries7.DailyPop(0, 0),
                new PopView.DailyPopSeries7.DailyPop(0, 0),
                new PopView.DailyPopSeries7.DailyPop(0, 0),
                new PopView.DailyPopSeries7.DailyPop(0, 0)
        ));

        // reportTime=now로 두면 shift/dayShift 영향이 없어서 테스트가 단순해짐
        when(snapshotQueryService.loadPopView(regionId, curKind))
                .thenReturn(new PopView(hourly, daily, now));

        NotificationRequest request = new NotificationRequest(
                List.of(regionId),
                now,
                null,
                null,
                null
        );

        // when: now 주입
        var events = rule.evaluate(request, now);

        // then
        assertThat(events).hasSize(1);
        assertThat(events.get(0).payload()).isInstanceOf(RainForecastPayload.class);

        RainForecastPayload payload = (RainForecastPayload) events.get(0).payload();

        assertThat(payload.hourlyParts()).containsExactly(
                new RainInterval(base.plusHours(3), base.plusHours(5))
        );
    }
}