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
import java.util.Map;

import static com.github.yun531.climate.util.time.TimeUtil.nowMinutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RainForecastRuleDayPartsTest {

    @Mock
    SnapshotQueryService snapshotQueryService;

    @Test
    void dayParts_ampm7x2_플래그_생성검증() {
        // given
        PopDailySeries7 daily = new PopDailySeries7(List.of(
                new PopDailySeries7.DailyPop(70, 10),
                new PopDailySeries7.DailyPop(10, 70),
                new PopDailySeries7.DailyPop(65, 65),
                new PopDailySeries7.DailyPop(0, 0),
                new PopDailySeries7.DailyPop(80, 0),
                new PopDailySeries7.DailyPop(0, 0),
                new PopDailySeries7.DailyPop(0, 0)
        ));
        int snapId = SnapKindEnum.SNAP_CURRENT.getCode();
        String regionId = "11B10101";

        // hourly는 의미 없으니 0으로 채움 (PopSeries24는 26개 필요)
        LocalDateTime base = nowMinutes().plusHours(5);
        PopSeries24 hourly = buildHourlySeries26(base, 0);

        when(snapshotQueryService.loadForecastSeries(regionId, snapId))
                .thenReturn(new PopForecastSeries(hourly, daily));

        SnapshotCacheProperties cacheProps = new SnapshotCacheProperties(180, 60, 165);
        RainForecastRule rule = new RainForecastRule(snapshotQueryService, cacheProps);

        // when
        NotificationRequest request = new NotificationRequest(
                List.of(regionId),
                LocalDateTime.parse("2025-11-18T08:00:00"),
                null,
                null,
                null
        );
        var events = rule.evaluate(request);

        // then
        assertThat(events).hasSize(1);
        Map<String, Object> payload = events.get(0).payload();

        @SuppressWarnings("unchecked")
        List<List<Integer>> dayParts = (List<List<Integer>>) payload.get("dayParts");

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

    private static PopSeries24 buildHourlySeries26(LocalDateTime base, int popAll) {
        List<PopSeries24.Point> points = new ArrayList<>(PopSeries24.SIZE);
        for (int i = 1; i <= PopSeries24.SIZE; i++) {
            points.add(new PopSeries24.Point(base.plusHours(i), popAll));
        }
        return new PopSeries24(points);
    }
}