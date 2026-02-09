package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.config.snapshot.SnapshotCacheProperties;
import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.PopView;
import com.github.yun531.climate.service.notification.model.payload.RainForecastPayload;
import com.github.yun531.climate.service.notification.model.payload.RainInterval;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import com.github.yun531.climate.service.snapshot.model.SnapKindEnum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.github.yun531.climate.util.time.TimeUtil.nowMinutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RainForecastRuleHourlyPartsTest {

    @Mock
    SnapshotQueryService snapshotQueryService;

    @Test
    void hourlyParts_연속구간_start_end_validAt_을_반환한다() {
        // given
        int snapId = SnapKindEnum.SNAP_CURRENT.getCode();
        String regionId = "11B10101";

        LocalDateTime now = nowMinutes();
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

        // PopView는 reportTime을 가진다고 가정(새 발표시 TTL 무관 갱신에 활용 가능)
        when(snapshotQueryService.loadPopView(regionId, snapId))
                .thenReturn(new PopView(hourly, daily, now));

        SnapshotCacheProperties cacheProps = new SnapshotCacheProperties(180, 60, 165);
        RainForecastRule rule = new RainForecastRule(snapshotQueryService, cacheProps);

        // when
        NotificationRequest request = new NotificationRequest(
                List.of(regionId),
                now,
                null,
                null,
                null
        );
        var events = rule.evaluate(request);

        // then
        assertThat(events).hasSize(1);

        assertThat(events.get(0).payload()).isInstanceOf(RainForecastPayload.class);
        RainForecastPayload payload = (RainForecastPayload) events.get(0).payload();

        assertThat(payload.hourlyParts()).containsExactly(
                new RainInterval(base.plusHours(3), base.plusHours(5))
        );
    }
}