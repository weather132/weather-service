package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.dto.PopForecastSeries;
import com.github.yun531.climate.dto.PopDailySeries7;
import com.github.yun531.climate.dto.PopSeries24;
import com.github.yun531.climate.dto.SnapKindEnum;
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
import java.util.Map;

import static com.github.yun531.climate.util.TimeUtil.nowMinutes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RainForecastRuleTest {

    @Mock
    ClimateService climateService;

    @Test
    void dayParts_ampm7x2_플래그_생성검증() {
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

        // 시간대별 POP 은 해당 테스트에서 의미가 없으므로 0으로 채움
        PopSeries24 hourly = new PopSeries24(Collections.nCopies(24, 0));

        when(climateService.loadForecastSeries(1, snapId))
                .thenReturn(new PopForecastSeries(hourly, daily));

        RainForecastRule rule = new RainForecastRule(climateService);

        // when
        NotificationRequest request = new NotificationRequest(
                List.of(1),
                LocalDateTime.parse("2025-11-18T08:00:00"), // since
                null,   // enabledTypes (룰에서 사용 안 함)
                null,   // filterWarningKinds
                null    // rainHourLimit
        );
        var events = rule.evaluate(request);

        // then
        assertThat(events).hasSize(1);

        Map<String, Object> payload = events.get(0).payload();

        @SuppressWarnings("unchecked")
        List<List<Integer>> dayParts = (List<List<Integer>>) payload.get("dayParts");

        // TH = 60 기준
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

    @Test
    void hourlyParts_연속구간_start_end_인덱스를_반환한다() {
        List<Integer> hourlyValues = new ArrayList<>(Collections.nCopies(24, 0));
        hourlyValues.set(2, 60);
        hourlyValues.set(3, 60);
        hourlyValues.set(4, 60);

        PopSeries24 hourly = new PopSeries24(hourlyValues);

        // 일자별 POP 은 해당 테스트에서 의미 없으므로 모두 0
        PopDailySeries7 daily = new PopDailySeries7(List.of(
                new PopDailySeries7.DailyPop(0, 0),
                new PopDailySeries7.DailyPop(0, 0),
                new PopDailySeries7.DailyPop(0, 0),
                new PopDailySeries7.DailyPop(0, 0),
                new PopDailySeries7.DailyPop(0, 0),
                new PopDailySeries7.DailyPop(0, 0),
                new PopDailySeries7.DailyPop(0, 0)
        ));

        int snapId = SnapKindEnum.SNAP_CURRENT.getCode();

        when(climateService.loadForecastSeries(1, snapId))
                .thenReturn(new PopForecastSeries(hourly, daily));

        RainForecastRule rule = new RainForecastRule(climateService);

        // when
        NotificationRequest request = new NotificationRequest(
                List.of(1),
                nowMinutes(),   // since: 첫 호출이라 어떤 값을 넣어도 계산됨
                null,
                null,
                null
        );
        var events = rule.evaluate(request);

        // then
        assertThat(events).hasSize(1);

        @SuppressWarnings("unchecked")
        List<List<Integer>> hourlyParts =
                (List<List<Integer>>) events.get(0).payload().get("hourlyParts");

        // 3~5시간 후 하나의 구간만 나와야 하므로 [3,5] 하나만 존재
        assertThat(hourlyParts).containsExactly(
                List.of(3, 5)
        );
    }
}