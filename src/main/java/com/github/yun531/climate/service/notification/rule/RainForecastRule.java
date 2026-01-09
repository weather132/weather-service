package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.notification.model.PopForecastSeries;
import com.github.yun531.climate.service.notification.model.RainForecastParts;
import com.github.yun531.climate.service.notification.model.RainThresholdEnum;
import com.github.yun531.climate.service.notification.rule.adjust.RainForecastPartsAdjuster;
import com.github.yun531.climate.service.notification.rule.compute.RainForecastComputer;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import com.github.yun531.climate.service.snapshot.model.SnapKindEnum;
import com.github.yun531.climate.util.cache.CacheEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.github.yun531.climate.util.time.TimeUtil.nowMinutes;

@Component
@RequiredArgsConstructor
public class RainForecastRule extends AbstractCachedRegionAlertRule<List<AlertEvent>> {

    private final SnapshotQueryService snapshotQueryService;

    private static final int SNAP_CURRENT_CODE = SnapKindEnum.SNAP_CURRENT.getCode();
    private static final int RAIN_THRESHOLD    = RainThresholdEnum.RAIN.getThreshold();
    private static final int RECOMPUTE_THRESHOLD_MINUTES = 165;

    private static final String PAYLOAD_SRC_RULE_KEY      = "_srcRule";
    private static final String PAYLOAD_SRC_RULE_NAME     = "RainForecastRule";
    private static final String PAYLOAD_HOURLY_PARTS_KEY  = "hourlyParts";
    private static final String PAYLOAD_DAY_PARTS_KEY     = "dayParts";

    private static final int MAX_HOURLY_HOURS = 24;
    private static final int MAX_SHIFT_HOURS  = 2; // 3시간 스냅샷을 0/1/2시간 재사용

    private final RainForecastComputer computer =
            new RainForecastComputer(RAIN_THRESHOLD, MAX_HOURLY_HOURS);

    private final RainForecastPartsAdjuster partsAdjuster =
            new RainForecastPartsAdjuster(
                    PAYLOAD_HOURLY_PARTS_KEY,
                    PAYLOAD_DAY_PARTS_KEY,
                    MAX_SHIFT_HOURS,
                    MAX_HOURLY_HOURS
            );

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.RAIN_FORECAST;
    }

    @Override
    protected int thresholdMinutes() {
        return RECOMPUTE_THRESHOLD_MINUTES;
    }

    @Override
    protected CacheEntry<List<AlertEvent>> computeForRegion(String regionId) {
        PopForecastSeries series = snapshotQueryService.loadForecastSeries(regionId, SNAP_CURRENT_CODE);
        if (series == null || (series.hourly() == null && series.daily() == null)) {
            return new CacheEntry<>(List.of(), null);
        }

        RainForecastParts parts = computer.compute(series);

        Map<String, Object> payload = Map.of(
                PAYLOAD_SRC_RULE_KEY, PAYLOAD_SRC_RULE_NAME,
                PAYLOAD_HOURLY_PARTS_KEY, parts.hourlyParts(),
                PAYLOAD_DAY_PARTS_KEY, parts.dayParts()
        );

        // PopForecastSeries 자체에 reportTime이 없으므로 "계산 시각"을 기준 시각으로 저장
        LocalDateTime computedAt = nowMinutes();
        AlertEvent event = new AlertEvent(AlertTypeEnum.RAIN_FORECAST, regionId, computedAt, payload);

        return new CacheEntry<>(List.of(event), computedAt);
    }

    @Override
    protected List<AlertEvent> buildEvents(String regionId,
                                           List<AlertEvent> events,
                                           LocalDateTime computedAt,
                                           LocalDateTime now,
                                           NotificationRequest request) {
        if (events == null || events.isEmpty()) return List.of();

        // 반환 직전에 parts(시간축) 보정
        List<AlertEvent> adjusted = partsAdjuster.adjust(events, computedAt, now);
        return (adjusted == null || adjusted.isEmpty()) ? List.of() : adjusted;
    }
}