package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.config.snapshot.SnapshotCacheProperties;
import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.PopDailySeries7;
import com.github.yun531.climate.service.notification.model.PopForecastSeries;
import com.github.yun531.climate.service.notification.model.PopSeries24;
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

        LocalDateTime base = nowMinutes().plusHours(5);

        List<PopSeries24.Point> points = new ArrayList<>(PopSeries24.SIZE);
        for (int i = 1; i <= PopSeries24.SIZE; i++) {
            LocalDateTime validAt = base.plusHours(i);
            int pop = (i == 3 || i == 4 || i == 5) ? 60 : 0;
            points.add(new PopSeries24.Point(validAt, pop));
        }
        PopSeries24 hourly = new PopSeries24(points);

        PopDailySeries7 daily = new PopDailySeries7(List.of(
                new PopDailySeries7.DailyPop(0, 0),
                new PopDailySeries7.DailyPop(0, 0),
                new PopDailySeries7.DailyPop(0, 0),
                new PopDailySeries7.DailyPop(0, 0),
                new PopDailySeries7.DailyPop(0, 0),
                new PopDailySeries7.DailyPop(0, 0),
                new PopDailySeries7.DailyPop(0, 0)
        ));

        when(snapshotQueryService.loadForecastSeries(regionId, snapId))
                .thenReturn(new PopForecastSeries(hourly, daily));

        SnapshotCacheProperties cacheProps = new SnapshotCacheProperties(180, 60, 165);
        RainForecastRule rule = new RainForecastRule(snapshotQueryService, cacheProps);

        // when
        NotificationRequest request = new NotificationRequest(
                List.of(regionId),
                nowMinutes(),
                null,
                null,
                null
        );
        var events = rule.evaluate(request);

        // then
        assertThat(events).hasSize(1);

        @SuppressWarnings("unchecked")
        List<List<LocalDateTime>> hourlyParts =
                (List<List<LocalDateTime>>) events.get(0).payload().get("hourlyParts");

        assertThat(hourlyParts).containsExactly(
                List.of(base.plusHours(3), base.plusHours(5))
        );
    }
}