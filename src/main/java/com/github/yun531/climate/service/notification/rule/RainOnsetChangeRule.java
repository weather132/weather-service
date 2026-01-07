package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.notification.model.PopSeriesPair;
import com.github.yun531.climate.service.notification.model.RainThresholdEnum;
import com.github.yun531.climate.service.notification.rule.adjust.RainOnsetEventOffsetAdjuster;
import com.github.yun531.climate.service.notification.rule.compute.RainOnsetEventComputer;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import com.github.yun531.climate.util.cache.CacheEntry;
import io.micrometer.common.lang.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RainOnsetChangeRule extends AbstractCachedRegionAlertRule<List<AlertEvent>> {

    private static final int RAIN_TH = RainThresholdEnum.RAIN.getThreshold();
    private static final int RECOMPUTE_THRESHOLD_MINUTES = 165;

    private static final String PAYLOAD_SRC_RULE_KEY  = "_srcRule";
    private static final String PAYLOAD_SRC_RULE_NAME = "RainOnsetChangeRule";
    private static final String PAYLOAD_HOUR_KEY      = "hourOffset";
    private static final String PAYLOAD_POP_KEY       = "pop";

    private static final int MAX_SHIFT_HOURS = 2;

    private final SnapshotQueryService snapshotQueryService;

    private final RainOnsetEventOffsetAdjuster offsetAdjuster =
            new RainOnsetEventOffsetAdjuster(PAYLOAD_HOUR_KEY, MAX_SHIFT_HOURS);

    private final RainOnsetEventComputer computer =
            new RainOnsetEventComputer(
                    RAIN_TH,
                    PAYLOAD_SRC_RULE_KEY,
                    PAYLOAD_SRC_RULE_NAME,
                    PAYLOAD_HOUR_KEY,
                    PAYLOAD_POP_KEY
            );

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.RAIN_ONSET;
    }

    @Override
    protected int thresholdMinutes() {
        return RECOMPUTE_THRESHOLD_MINUTES;
    }

    @Override
    protected CacheEntry<List<AlertEvent>> computeForRegion(String regionId) {
        PopSeriesPair series = snapshotQueryService.loadDefaultPopSeries(regionId);

        if (series == null || series.current() == null || series.previous() == null) {
            return new CacheEntry<>(List.of(), null);
        }

        LocalDateTime computedAt = series.curReportTime();
        List<AlertEvent> events = computer.detect(regionId, series, computedAt);

        return new CacheEntry<>(events, computedAt);
    }

    @Override
    protected List<AlertEvent> buildEvents(String regionId,
                                           List<AlertEvent> events,
                                           @Nullable LocalDateTime computedAt,
                                           LocalDateTime now,
                                           NotificationRequest request) {
        if (events == null || events.isEmpty()) return List.of();

        // 반환 직전에만 hourOffset / occurredAt 보정
        List<AlertEvent> adjusted = offsetAdjuster.adjust(events, computedAt, now);
        if (adjusted == null || adjusted.isEmpty()) return List.of();

        Integer maxHour = request.rainHourLimit();
        if (maxHour != null) {
            adjusted = filterByMaxHour(adjusted, maxHour);
        }

        return (adjusted == null || adjusted.isEmpty()) ? List.of() : adjusted;
    }

    private List<AlertEvent> filterByMaxHour(List<AlertEvent> events, int maxHourInclusive) {
        List<AlertEvent> out = new ArrayList<>();
        for (AlertEvent e : events) {
            Integer h = extractHour(e);
            if (h == null || h <= maxHourInclusive) out.add(e);
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    @Nullable
    private Integer extractHour(AlertEvent event) {
        Map<String, Object> payload = event.payload();
        if (payload == null) return null;

        Object v = payload.get(PAYLOAD_HOUR_KEY);
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        return null;
    }
}