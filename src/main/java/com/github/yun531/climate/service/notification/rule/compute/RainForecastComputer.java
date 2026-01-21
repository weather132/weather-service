package com.github.yun531.climate.service.notification.rule.compute;

import com.github.yun531.climate.service.notification.model.PopDailySeries7;
import com.github.yun531.climate.service.notification.model.PopForecastSeries;
import com.github.yun531.climate.service.notification.model.PopSeries24;
import com.github.yun531.climate.service.notification.model.RainForecastParts;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * PopForecastSeries -> RainForecastParts 계산만 담당
 */
public class RainForecastComputer {

    private final int rainThreshold;
    private final int maxHourlyHours;

    public RainForecastComputer(int rainThreshold, int maxHourlyHours) {
        this.rainThreshold = rainThreshold;
        this.maxHourlyHours = maxHourlyHours;
    }

    public RainForecastParts compute(PopForecastSeries series) {
        return new RainForecastParts(
                buildHourlyParts(series),
                buildDayParts(series)
        );
    }

    private List<RainForecastParts.HourlyPart> buildHourlyParts(PopForecastSeries fs) {
        PopSeries24 hourly = fs.hourly();
        if (hourly == null) return List.of();

        List<PopSeries24.Point> raw = hourly.points();
        if (raw.isEmpty()) return List.of();

        List<PopSeries24.Point> points = normalizeHourlyPoints(raw, maxHourlyHours);
        if (points.isEmpty()) return List.of();

        List<RainForecastParts.HourlyPart> parts = computeRainSegments(points, rainThreshold);
        return parts.isEmpty() ? List.of() : List.copyOf(parts);
    }

    /** 원본 포인트를 "시간 구간 계산 가능한 형태"로 정규화한다. */
    private List<PopSeries24.Point> normalizeHourlyPoints(List<PopSeries24.Point> raw, int maxHourlyHours) {
        // validAt 기준 정렬 (null은 뒤로)
        List<PopSeries24.Point> sorted = raw.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        PopSeries24.Point::validAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();

        int maxCount = Math.max(0, Math.min(maxHourlyHours, sorted.size()));

        // validAt 없는 포인트는 시간구간 계산 불가 → 제외 + 상한 적용
        return sorted.stream()
                .filter(p -> p.validAt() != null)
                .limit(maxCount)
                .toList();
    }

    /**  POP 임계치 이상이 연속되는 구간을 HourlyPart로 만든다.
     *  - 간격 체크 없음(입력 points는 시간순 정렬되어 있다고 가정)
     *  - 구간의 끝은 "직전 시각(prevAt)" 포함                    */
    private List<RainForecastParts.HourlyPart> computeRainSegments(
            List<PopSeries24.Point> points,
            int rainThreshold
    ) {
        List<RainForecastParts.HourlyPart> parts = new ArrayList<>();

        boolean inRain = false;
        LocalDateTime segStart = null;
        LocalDateTime prevAt = null;

        for (PopSeries24.Point p : points) {
            LocalDateTime at = p.validAt();
            int pop = p.pop();

            if (pop >= rainThreshold) {
                if (!inRain) {
                    inRain = true;
                    segStart = at;
                }
            } else {
                if (inRain) {
                    parts.add(new RainForecastParts.HourlyPart(segStart, prevAt));
                    inRain = false;
                    segStart = null;
                }
            }
            prevAt = at;
        }

        // 마지막이 비 구간으로 끝났으면 마감
        if (inRain && segStart != null && prevAt != null) {
            parts.add(new RainForecastParts.HourlyPart(segStart, prevAt));
        }

        return parts;
    }

    private List<RainForecastParts.DayPart> buildDayParts(PopForecastSeries fs) {
        PopDailySeries7 daily = fs.daily();
        if (daily == null || daily.days().isEmpty()) return List.of();

        List<RainForecastParts.DayPart> parts = new ArrayList<>(daily.days().size());

        // dayOffset 유지 (0~6)
        for (int dayOffset = 0; dayOffset < daily.days().size(); dayOffset++) {
            PopDailySeries7.DailyPop d = daily.days().get(dayOffset);
            boolean am = d.am() >= rainThreshold;
            boolean pm = d.pm() >= rainThreshold;
            parts.add(new RainForecastParts.DayPart(dayOffset, am, pm));
        }

        return parts.isEmpty() ? List.of() : List.copyOf(parts);
    }
}