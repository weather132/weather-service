package com.github.yun531.climate.notification.domain.compute;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload.DailyRainFlags;
import com.github.yun531.climate.notification.domain.payload.RainForecastPayload.RainInterval;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.shared.time.TimeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * PopView -> 비 예보 요약 AlertEvent 계산.
 * - 24시간 이내 비 구간(hourlyParts) + 7일 오전/오후 플래그(dayParts)를 산출
 */
public class RainForecastComputer {

    private static final Logger log = LoggerFactory.getLogger(RainForecastComputer.class);

    private final int rainThreshold;
    private final int maxHourlyPoints;

    public RainForecastComputer(int rainThreshold, int maxHourlyPoints) {
        this.rainThreshold = rainThreshold;
        this.maxHourlyPoints = Math.max(0, maxHourlyPoints);
    }

    /**
     * @return 비 예보 요약 AlertEvents (데이터 없으면 null)
     */
    public AlertEvent compute(String regionId, PopView view, LocalDateTime now) {
        if (regionId == null || regionId.isBlank()) return null;
        if (view == null) return null;
        if (now == null) return null;

        List<RainInterval> hourlyRanges = buildHourlyRanges(view);
        List<DailyRainFlags> dayFlags = buildDayFlags(view);

        if (log.isInfoEnabled()) logInfo(view, hourlyRanges);

        RainForecastPayload payload = new RainForecastPayload(
                AlertTypeEnum.RAIN_FORECAST.ruleId(), hourlyRanges, dayFlags
        );

        LocalDateTime computedAt = TimeUtil.truncateToMinutes(
                view.reportTime() != null ? view.reportTime() : now);
        return new AlertEvent(AlertTypeEnum.RAIN_FORECAST, regionId, computedAt, payload);
    }

    // -- 시간별: POP 임계치 이상이 연속되는 구간을 RainInterval로 만듦 --

    private List<RainInterval> buildHourlyRanges(PopView view) {
        List<PopView.HourlyPopSeries26.Point> raw = view.hourly().points();
        if (raw == null || raw.isEmpty() || maxHourlyPoints == 0) return List.of();

        List<PopView.HourlyPopSeries26.Point> points =
                new ArrayList<>(Math.min(raw.size(), maxHourlyPoints));

        for (PopView.HourlyPopSeries26.Point p : raw) {
            if (p == null) continue;
            if (p.validAt() == null) break;

            points.add(p);
            if (points.size() == maxHourlyPoints) break;
        }

        if (points.isEmpty()) return List.of();
        return computeRainSegments(points);
    }

    // 연속된 "비 구간"(pop >= rainThreshold)을 [start, end] 절대시각 구간으로 묶음
    private List<RainInterval> computeRainSegments(List<PopView.HourlyPopSeries26.Point> points) {
        List<RainInterval> parts = new ArrayList<>();
        boolean inRain = false;          // 현재 순회 위치가 "비 구간 내부"인지 여부
        LocalDateTime segStart = null;   // 비 구간이 시작된 시각
        LocalDateTime prevAt = null;     // 직전 포인트의 시각. 구간을 닫을 때(end)로 사용.

        for (PopView.HourlyPopSeries26.Point p : points) {
            LocalDateTime at = p.validAt();
            int pop = p.pop();

            if (pop >= rainThreshold) {
                if (!inRain) {
                    inRain = true;
                    segStart = at;
                }
            } else {
                if (inRain) {
                    parts.add(new RainInterval(segStart, prevAt));
                    inRain = false;
                    segStart = null;
                }
            }
            prevAt = at;
        }

        // 루프가 끝났는데도 비 구간이 열려 있으면, 마지막 유효 시각(prevAt)까지를 구간으로 닫음
        if (inRain && segStart != null && prevAt != null) {
            parts.add(new RainInterval(segStart, prevAt));
        }

        return parts.isEmpty() ? List.of() : List.copyOf(parts);
    }

    // -- 일별: dayOffset(0~6) 오전/오후 비 플래그 --

    private List<DailyRainFlags> buildDayFlags(PopView view) {
        List<PopView.DailyPopSeries7.DailyPop> days = view.daily().days();
        if (days == null || days.isEmpty()) return List.of();

        List<DailyRainFlags> flags = new ArrayList<>(days.size());
        for (PopView.DailyPopSeries7.DailyPop d : days) {
            flags.add(new DailyRainFlags(d.am() >= rainThreshold, d.pm() >= rainThreshold));
        }
        return List.copyOf(flags);
    }

    // -- 로깅 --

    private void logInfo(PopView view, List<RainInterval> hourlyRanges) {
        var pts = view.hourly().points();
        long nullValidAt = 0;
        long rainCntInFirst24 = 0;
        int seenValid = 0;

        for (var p : pts) {
            if (p == null || p.validAt() == null) { nullValidAt++; continue; }
            if (seenValid < 24 && p.pop() >= rainThreshold) rainCntInFirst24++;
            seenValid++;
        }

        log.info("[RAIN_FORECAST] points={}, nullValidAt={}, rainCntInFirst24={}, segments={}",
                pts.size(), nullValidAt, rainCntInFirst24, hourlyRanges.size());
    }
}