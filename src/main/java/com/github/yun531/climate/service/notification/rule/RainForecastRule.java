package com.github.yun531.climate.service.notification.rule;

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
import com.github.yun531.climate.util.time.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.List;

public class RainForecastRule extends AbstractCachedRegionAlertRule<AlertEvent> {

    private static final Logger log = LoggerFactory.getLogger(RainForecastRule.class);

    private static final int SNAP_CURRENT_CODE = SnapKindEnum.SNAP_CURRENT.getCode();

    private final SnapshotQueryService snapshotQueryService;
    private final RainForecastComputer computer;

    private final RainForecastPartsAdjuster adjuster;

    private final int recomputeThresholdMinutes;
    private final int rainThreshold;

    public RainForecastRule(
            SnapshotQueryService snapshotQueryService,
            RainForecastComputer computer,
            RainForecastPartsAdjuster adjuster,
            int recomputeThresholdMinutes,
            int rainThreshold
    ) {
        this.snapshotQueryService = snapshotQueryService;
        this.computer = computer;
        this.adjuster = adjuster;
        this.recomputeThresholdMinutes = Math.max(0, recomputeThresholdMinutes);
        this.rainThreshold = rainThreshold;
    }

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.RAIN_FORECAST;
    }

    @Override
    protected int thresholdMinutes() {
        return recomputeThresholdMinutes;
    }

    @Override
    protected CacheEntry<AlertEvent> computeForRegion(String regionId, LocalDateTime now) {
        PopView view = snapshotQueryService.loadPopView(regionId, SNAP_CURRENT_CODE);
        if (view == null) {
            return new CacheEntry<>(null, null);
        }

        RainForecastParts parts = computer.compute(view);

        if (log.isInfoEnabled()) {  //todo log 확인
            logInfo(view, parts);
        }

        // ===== 타입 payload 구성 =====
        List<RainInterval> hourlyRanges = parts.hourlyParts().stream()
                .map(p -> new RainInterval(p.start(), p.end()))
                .toList();

        List<DailyRainFlags> dayFlags = parts.dayParts().stream()
                .map(d -> new DailyRainFlags(d.am(), d.pm()))
                .toList(); // dayOffset 순서가 이미 0..6으로 들어오므로 정렬 불필요

        RainForecastPayload payload = new RainForecastPayload(RuleId.RAIN_FORECAST.id(), hourlyRanges, dayFlags);
        // ===========================

        // computedAt: reportTime 우선, 없으면 now(분단위)
        LocalDateTime computedAt =
                (view.reportTime() != null)
                        ? TimeUtil.truncateToMinutes(view.reportTime())
                        : TimeUtil.truncateToMinutes(now);

        AlertEvent event = new AlertEvent(AlertTypeEnum.RAIN_FORECAST, regionId, computedAt, payload);
        return new CacheEntry<>(event, computedAt);
    }

    @Override
    protected List<AlertEvent> buildEvents(
            String regionId,
            AlertEvent event,
            @Nullable LocalDateTime computedAt,
            LocalDateTime now,
            NotificationRequest request
    ) {
        if (event == null) return List.of();

        // 반환 직전에 시간축 보정(typed payload 직접 보정)
        AlertEvent adjusted = adjuster.adjust(event, computedAt, now);
        return (adjusted == null) ? List.of() : List.of(adjusted);
    }

    private void logInfo(PopView view, RainForecastParts parts) {
        var pts = view.hourly().points();

        long nullValidAt = 0;
        long rainCntInFirst24 = 0;
        int seenValid = 0;

        for (var p : pts) {
            if (p == null || p.validAt() == null) {
                nullValidAt++;
                continue;
            }
            if (seenValid < 24 && p.pop() >= rainThreshold) rainCntInFirst24++;
            seenValid++;
        }

        log.info("[RAIN_FORECAST] points={}, nullValidAt={}, rainCntInFirst24={}, segments={}",
                pts.size(), nullValidAt, rainCntInFirst24, parts.hourlyParts().size());
    }
}