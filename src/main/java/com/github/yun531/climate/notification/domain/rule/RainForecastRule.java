package com.github.yun531.climate.notification.domain.rule;

import com.github.yun531.climate.notification.domain.model.*;
import com.github.yun531.climate.notification.domain.payload.DailyRainFlags;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload;
import com.github.yun531.climate.notification.domain.payload.RainInterval;
import com.github.yun531.climate.notification.domain.port.PopViewReadPort;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.rule.adjust.RainForecastPartsAdjuster;
import com.github.yun531.climate.notification.domain.rule.compute.RainForecastComputer;
import com.github.yun531.climate.notification.application.command.GenerateAlertsCommand;
import com.github.yun531.climate.util.cache.CacheEntry;
import com.github.yun531.climate.util.time.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.List;

public class RainForecastRule extends AbstractCachedRegionAlertRule<AlertEvent> {

    private static final Logger log = LoggerFactory.getLogger(RainForecastRule.class);

    private final PopViewReadPort popViewReadPort;
    private final RainForecastComputer computer;
    private final RainForecastPartsAdjuster adjuster;

    private final int recomputeThresholdMinutes;
    private final int rainThreshold;

    public RainForecastRule(
            PopViewReadPort popViewReadPort,
            RainForecastComputer computer,
            RainForecastPartsAdjuster adjuster,
            int recomputeThresholdMinutes,
            int rainThreshold
    ) {
        this.popViewReadPort = popViewReadPort;
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
        PopView view = popViewReadPort.loadCurrent(regionId);
        if (view == null) {
            return new CacheEntry<>(null, TimeUtil.truncateToMinutes(now));
        }

        RainForecastParts parts = computer.compute(view);

        if (log.isInfoEnabled()) logInfo(view, parts);

        List<RainInterval> hourlyRanges = parts.hourlyParts().stream()
                .map(p -> new RainInterval(p.start(), p.end()))
                .toList();

        List<DailyRainFlags> dayFlags = parts.dayParts().stream()
                .map(d -> new DailyRainFlags(d.am(), d.pm()))
                .toList();

        RainForecastPayload payload = new RainForecastPayload(
                RuleId.RAIN_FORECAST.id(),
                hourlyRanges,
                dayFlags
        );

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
            GenerateAlertsCommand command
    ) {
        if (event == null) return List.of();
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