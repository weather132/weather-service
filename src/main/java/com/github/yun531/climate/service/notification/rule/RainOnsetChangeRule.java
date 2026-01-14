package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.notification.model.PopSeriesPair;
import com.github.yun531.climate.service.notification.model.RainThresholdEnum;
import com.github.yun531.climate.service.notification.rule.adjust.RainOnsetEventValidAtAdjuster;
import com.github.yun531.climate.service.notification.rule.compute.RainOnsetEventComputer;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import com.github.yun531.climate.util.cache.CacheEntry;
import io.micrometer.common.lang.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RainOnsetChangeRule extends AbstractCachedRegionAlertRule<List<AlertEvent>> {

    private static final int RAIN_TH = RainThresholdEnum.RAIN.getThreshold();
    private static final int RECOMPUTE_THRESHOLD_MINUTES = 165;

    private static final String PAYLOAD_SRC_RULE_KEY   = "_srcRule";
    private static final String PAYLOAD_SRC_RULE_NAME  = "RainOnsetChangeRule";
    private static final String PAYLOAD_VALID_AT_KEY   = "validAt";
    private static final String PAYLOAD_POP_KEY        = "pop";

    // now 기준으로 1~24시간 윈도우만 반환
    private static final int WINDOW_HOURS = 24;

    private final SnapshotQueryService snapshotQueryService;

    // baseTime 없이 now 기준 윈도우(+1~+24)로 제한
    private final RainOnsetEventValidAtAdjuster  windowAdjuster =
            new RainOnsetEventValidAtAdjuster(PAYLOAD_VALID_AT_KEY, WINDOW_HOURS);

    // (앞서 수정한) validAt 기반 RainOnsetEventComputer를 사용
    private final RainOnsetEventComputer computer =
            new RainOnsetEventComputer(
                    RAIN_TH,
                    PAYLOAD_SRC_RULE_KEY,
                    PAYLOAD_SRC_RULE_NAME,
                    PAYLOAD_VALID_AT_KEY,
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

        // 캐시 computedAt은 기존처럼 reportTime 사용(재계산 정책/로그 목적)
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

        // baseTime 없이 now 기준 윈도우(+1~+24)로 제한 + occurredAt을 nowHour로 통일
        List<AlertEvent> adjusted = windowAdjuster.adjust(events, now);
        if (adjusted == null || adjusted.isEmpty()) return List.of();

        Integer maxHour = request.rainHourLimit();
        if (maxHour != null) {
            adjusted = filterByMaxHour(adjusted, maxHour, now);
        }

        return adjusted.isEmpty() ? List.of() : List.copyOf(adjusted);
    }

    /**
     * rainHourLimit: now 기준 N시간 이내만 남김
     * - validAt <= nowHour + maxHourInclusive
     */
    private List<AlertEvent> filterByMaxHour(List<AlertEvent> events, int maxHourInclusive, LocalDateTime now) {
        LocalDateTime nowHour = now.truncatedTo(ChronoUnit.HOURS);
        LocalDateTime limit = nowHour.plusHours(maxHourInclusive);

        List<AlertEvent> out = new ArrayList<>();
        for (AlertEvent e : events) {
            LocalDateTime at = extractValidAt(e);
            if (at == null || !at.isAfter(limit)) { // at <= limit
                out.add(e);
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    @Nullable
    private LocalDateTime extractValidAt(AlertEvent event) {
        Map<String, Object> payload = event.payload();
        if (payload == null) return null;

        Object v = payload.get(PAYLOAD_VALID_AT_KEY);

        if (v instanceof LocalDateTime t) return t;

        if (v instanceof String s) {
            try {
                return LocalDateTime.parse(s);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }

        return null;
    }
}