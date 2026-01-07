package com.github.yun531.climate.service.notification.rule.compute;

import com.github.yun531.climate.service.notification.model.PopDailySeries7;
import com.github.yun531.climate.service.notification.model.PopForecastSeries;
import com.github.yun531.climate.service.notification.model.PopSeries24;

import java.util.ArrayList;
import java.util.List;

/**
 * PopForecastSeries -> (hourlyParts, dayParts) 계산만 담당
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

    private List<List<Integer>> buildHourlyParts(PopForecastSeries series) {
        PopSeries24 hourly = series.hourly();
        if (hourly == null) return List.of();

        int maxOffset = Math.min(hourly.size(), maxHourlyHours);
        if (maxOffset <= 0) return List.of();

        List<List<Integer>> parts = new ArrayList<>();
        int offset = 1;

        while (offset <= maxOffset) {
            int start = findNextRainStart(hourly, offset, maxOffset);
            if (start == -1) break;

            int end = findRainEnd(hourly, start, maxOffset);
            parts.add(List.of(start, end));

            offset = end + 1;
        }

        return parts.isEmpty() ? List.of() : List.copyOf(parts);
    }

    private int findNextRainStart(PopSeries24 series, int startOffset, int maxOffset) {
        int o = startOffset;
        while (o <= maxOffset && series.get(o) < rainThreshold) o++;
        return (o > maxOffset) ? -1 : o;
    }

    private int findRainEnd(PopSeries24 series, int startOffset, int maxOffset) {
        int o = startOffset;
        while (o + 1 <= maxOffset && series.get(o + 1) >= rainThreshold) o++;
        return o;
    }

    private List<List<Integer>> buildDayParts(PopForecastSeries fs) {
        PopDailySeries7 daily = fs.daily();
        if (daily == null || daily.days() == null || daily.days().isEmpty()) return List.of();

        List<List<Integer>> parts = new ArrayList<>(daily.days().size());
        for (PopDailySeries7.DailyPop d : daily.days()) {
            int am = (d.am() >= rainThreshold) ? 1 : 0;
            int pm = (d.pm() >= rainThreshold) ? 1 : 0;
            parts.add(List.of(am, pm));
        }

        return parts.isEmpty() ? List.of() : List.copyOf(parts);
    }

    public record RainForecastParts(List<List<Integer>> hourlyParts, List<List<Integer>> dayParts) {}
}