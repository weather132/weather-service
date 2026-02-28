package com.github.yun531.climate.notification.domain.rule;

import com.github.yun531.climate.notification.domain.model.*;
import com.github.yun531.climate.notification.domain.payload.DailyRainFlags;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload;
import com.github.yun531.climate.notification.domain.payload.RainInterval;
import com.github.yun531.climate.notification.domain.port.PopViewReadPort;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.rule.adjust.RainForecastPartsAdjuster;
import com.github.yun531.climate.notification.domain.rule.compute.RainForecastComputer;
import com.github.yun531.climate.notification.domain.rule.criteria.AlertCriteria;
import com.github.yun531.climate.shared.time.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

public class RainForecastRule implements AlertRule {

    private static final Logger log = LoggerFactory.getLogger(RainForecastRule.class);

    private final PopViewReadPort popViewReadPort;
    private final RainForecastComputer computer;
    private final RainForecastPartsAdjuster adjuster;

    private final int rainThreshold;

    public RainForecastRule(
            PopViewReadPort popViewReadPort,
            RainForecastComputer computer,
            RainForecastPartsAdjuster adjuster,
            int rainThreshold
    ) {
        this.popViewReadPort = popViewReadPort;
        this.computer = computer;
        this.adjuster = adjuster;
        this.rainThreshold = rainThreshold;
    }

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.RAIN_FORECAST;
    }

    @Override
    public List<AlertEvent> evaluate(String regionId, AlertCriteria criteria, LocalDateTime now) {
        if (regionId == null || regionId.isBlank()) return List.of();

        LocalDateTime effectiveNow = (now == null)
                ? TimeUtil.nowMinutes()
                : TimeUtil.truncateToMinutes(now);

        PopView view = popViewReadPort.loadCurrent(regionId);
        if (view == null) return List.of();

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
                        : effectiveNow;

        AlertEvent raw = new AlertEvent(AlertTypeEnum.RAIN_FORECAST, regionId, computedAt, payload);

        AlertEvent adjusted = adjuster.adjust(raw, computedAt, effectiveNow);
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