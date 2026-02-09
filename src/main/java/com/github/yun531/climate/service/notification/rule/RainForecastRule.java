package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.config.snapshot.SnapshotCacheProperties;
import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.*;
import com.github.yun531.climate.service.notification.model.payload.DailyRainFlags;
import com.github.yun531.climate.service.notification.model.payload.RainForecastPayload;
import com.github.yun531.climate.service.notification.model.payload.RainInterval;
import com.github.yun531.climate.service.notification.rule.adjust.RainForecastPartsAdjuster;
import com.github.yun531.climate.service.notification.rule.compute.RainForecastComputer;
import com.github.yun531.climate.service.query.SnapshotQueryService;
import com.github.yun531.climate.service.snapshot.model.SnapKindEnum;
import com.github.yun531.climate.util.cache.CacheEntry;
import io.micrometer.common.lang.Nullable;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import static com.github.yun531.climate.util.time.TimeUtil.nowMinutes;

@Component
@RequiredArgsConstructor
public class RainForecastRule extends AbstractCachedRegionAlertRule<List<AlertEvent>> {

    private static final Logger log = LoggerFactory.getLogger(RainForecastRule.class);

    private final SnapshotQueryService snapshotQueryService;
    private final SnapshotCacheProperties cacheProps;

    private static final int SNAP_CURRENT_CODE = SnapKindEnum.SNAP_CURRENT.getCode();
    private static final int RAIN_THRESHOLD = RainThresholdEnum.RAIN.getThreshold();

    private static final String SRC_RULE_NAME = "RainForecastRule";

    private static final int MAX_HOURLY_HOURS = 26;
    private static final int MAX_SHIFT_HOURS  = 2; // 3시간 스냅샷을 0/1/2시간 재사용

    private final RainForecastComputer computer =
            new RainForecastComputer(RAIN_THRESHOLD, MAX_HOURLY_HOURS);

    /** Map 파싱 없이, 타입 payload(RainForecastPayload)를 직접 보정 */
    private final RainForecastPartsAdjuster windowAdjuster =
            new RainForecastPartsAdjuster(MAX_SHIFT_HOURS, MAX_HOURLY_HOURS);

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.RAIN_FORECAST;
    }

    @Override
    protected int thresholdMinutes() {
        return cacheProps.recomputeThresholdMinutes();
    }

    @Override
    protected CacheEntry<List<AlertEvent>> computeForRegion(String regionId) {
        PopView view = snapshotQueryService.loadPopView(regionId, SNAP_CURRENT_CODE);
        if (view == null) {
            return new CacheEntry<>(List.of(), null);
        }

        RainForecastParts parts = computer.compute(view);

        var pts = view.hourly().points();
        long nullValidAt = pts.stream().filter(p -> p == null || p.validAt() == null).count();
        long rainCnt24 = pts.stream()
                .filter(p -> p != null)
                .sorted(Comparator.comparing(
                        PopView.HourlyPopSeries26.Point::validAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .limit(24)
                .filter(p -> p.validAt() != null && p.pop() >= RAIN_THRESHOLD)
                .count();

        log.info("[RAIN] points={}, nullValidAt={}, rainCntInFirst24={}, parts={}",
                pts.size(), nullValidAt, rainCnt24, parts.hourlyParts().size());

        // ===== 타입 payload 구성 =====
        List<RainInterval> hourlyRanges =
                parts.hourlyParts().stream()
                        .map(p -> new RainInterval(p.start(), p.end()))
                        .toList();

        List<DailyRainFlags> dayFlags =
                parts.dayParts().stream()
                        .sorted(Comparator.comparing(RainForecastParts.DayPart::dayOffset))
                        .map(d -> new DailyRainFlags(d.am(), d.pm()))
                        .toList();

        RainForecastPayload payload =
                new RainForecastPayload(SRC_RULE_NAME, hourlyRanges, dayFlags);
        // ===========================

        // now 대신 reportTime 우선(새 발표 반영에 유리)
        LocalDateTime computedAt = (view.reportTime() != null) ? view.reportTime() : nowMinutes();

        AlertEvent event = new AlertEvent(AlertTypeEnum.RAIN_FORECAST, regionId, computedAt, payload);
        return new CacheEntry<>(List.of(event), computedAt);
    }

    @Override
    protected List<AlertEvent> buildEvents(String regionId,
                                           List<AlertEvent> events,
                                           @Nullable LocalDateTime computedAt,
                                           LocalDateTime now,
                                           NotificationRequest request) {
        if (events == null || events.isEmpty()) return List.of();

        // 반환 직전에 시간축 보정 (payload를 타입으로 직접 다룸)
        List<AlertEvent> adjusted = windowAdjuster.adjust(events, computedAt, now);
        return (adjusted == null || adjusted.isEmpty()) ? List.of() : adjusted;
    }
}