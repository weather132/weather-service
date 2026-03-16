package com.github.yun531.climate.notification.domain.detect;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload.DailyRainFlags;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload.RainInterval;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.readmodel.PopView.Hourly;
import com.github.yun531.climate.notification.domain.readmodel.PopView.Daily;
import com.github.yun531.climate.shared.time.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * PopView → 비 예보 요약 AlertEvent 계산.
 * - hourlyParts: POP >= 임계치인 연속 시간을 구간(RainInterval)으로 묶음
 * - dayParts: 7일 오전/오후별 비 여부 플래그
 */
public class RainForecastDetector {

    private static final Logger log = LoggerFactory.getLogger(RainForecastDetector.class);

    private final int rainThreshold;
    private final int maxHourlyPoints;

    public RainForecastDetector(int rainThreshold, int maxHourlyPoints) {
        this.rainThreshold = rainThreshold;
        this.maxHourlyPoints = Math.max(0, maxHourlyPoints);
    }

    /** PopView 에서 비 예보 요약 AlertEvent를 생성. 데이터 없으면 null */
    @Nullable
    public AlertEvent detect(String regionId, PopView view, LocalDateTime now) {
        if (regionId == null || regionId.isBlank()) return null;
        if (view == null || now == null) return null;

        List<RainInterval> hourlyRanges = buildHourlyRanges(view);
        List<DailyRainFlags> dayFlags = buildDayFlags(view);

        if (log.isInfoEnabled()) logSummary(view, hourlyRanges);

        LocalDateTime occurredAt = TimeUtil.truncateToMinutes(
                view.reportTime() != null ? view.reportTime() : now);

        RainForecastPayload payload = new RainForecastPayload(
                AlertTypeEnum.RAIN_FORECAST, hourlyRanges, dayFlags);
        return new AlertEvent(AlertTypeEnum.RAIN_FORECAST, regionId, occurredAt, payload);
    }

    // =====================================================================
    //  시간별: POP >= 임계치인 연속 구간을 RainInterval로 묶음
    // =====================================================================

    private List<RainInterval> buildHourlyRanges(PopView view) {
        List<Hourly.Pop> pops = collectValidPoints(view.hourly().pops());
        return pops.isEmpty() ? List.of() : toRainIntervals(pops);
    }

    /** validAt이 있는 포인트만 최대 maxHourlyPoints개 수집 */
    private List<Hourly.Pop> collectValidPoints(List<Hourly.Pop> hourlyPops) {
        if (hourlyPops == null || hourlyPops.isEmpty() || maxHourlyPoints == 0) return List.of();

        List<Hourly.Pop> validHourlyPops = new ArrayList<>(Math.min(hourlyPops.size(), maxHourlyPoints));
        for (Hourly.Pop pop : hourlyPops) {
            if (pop == null) continue;
            if (pop.validAt() == null) continue;
            validHourlyPops.add(pop);
            if (validHourlyPops.size() == maxHourlyPoints) break;
        }
        return validHourlyPops;
    }

    /** 연속된 비 구간(pop >= threshold)을 [start, end] 절대시각 구간으로 묶음 */
    private List<RainInterval> toRainIntervals(List<Hourly.Pop> hourlyPops) {
        List<RainInterval> rainIntervals  = new ArrayList<>();
        boolean inRain = false;
        LocalDateTime segStart = null;
        LocalDateTime prevAt = null;

        for (Hourly.Pop pop : hourlyPops) {
            LocalDateTime at = pop.validAt();

            if (isRainy(pop)) {
                if (!inRain) {
                    inRain = true;
                    segStart = at;
                }
            } else if (inRain) {
                rainIntervals .add(new RainInterval(segStart, prevAt));
                inRain = false;
                segStart = null;
            }
            prevAt = at;
        }

        // 열린 구간 닫기
        if (inRain && segStart != null && prevAt != null) {
            rainIntervals .add(new RainInterval(segStart, prevAt));
        }

        return rainIntervals .isEmpty() ? List.of() : List.copyOf(rainIntervals );
    }

    private boolean isRainy(Hourly.Pop p) {
        return p.pop() != null && p.pop() >= rainThreshold;
    }

    // =====================================================================
    //  일별: dayOffset(0~6) 오전/오후 비 플래그
    // =====================================================================

    private List<DailyRainFlags> buildDayFlags(PopView view) {
        List<Daily.Pop> dailyPops = view.daily().pops();
        if (dailyPops == null || dailyPops.isEmpty()) return List.of();

        List<DailyRainFlags> flags = new ArrayList<>(dailyPops.size());
        for (Daily.Pop d : dailyPops) {
            flags.add(new DailyRainFlags(
                    d.am() != null && d.am() >= rainThreshold,
                    d.pm() != null && d.pm() >= rainThreshold));
        }
        return List.copyOf(flags);
    }

    // -- 로깅 --

    private void logSummary(PopView view, List<RainInterval> hourlyRanges) {
        var pops = view.hourly().pops();
        int nullCount = 0;
        int rainCount = 0;

        for (var pop : pops) {
            if (pop == null || pop.validAt() == null) { nullCount++; continue; }
            if (isRainy(pop)) rainCount++;
        }

        log.info("[RAIN_FORECAST] pops={}, nullValidAt={}, rainyPoints={}, segments={}",
                pops.size(), nullCount, rainCount, hourlyRanges.size());
    }
}