package com.github.yun531.climate.notification.domain.rule.compute;

import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.model.RainForecastParts;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** PopView -> RainForecastParts 계산만 담당 */
public class RainForecastComputer {

    private final int rainThreshold;
    private final int maxHourlyHours;

    public RainForecastComputer(int rainThreshold, int maxHourlyHours) {
        this.rainThreshold = rainThreshold;
        this.maxHourlyHours = Math.max(0, maxHourlyHours);
    }

    public RainForecastParts compute(PopView view) {
        if (view == null) return new RainForecastParts(List.of(), List.of());

        List<RainForecastParts.HourlyPart> hourly = buildHourlyParts(view);
        List<RainForecastParts.DayPart> daily = buildDayParts(view);

        return new RainForecastParts(hourly, daily);
    }

    private List<RainForecastParts.HourlyPart> buildHourlyParts(PopView view) {
        List<PopView.HourlyPopSeries26.Point> raw = view.hourly().points();
        if (raw == null || raw.isEmpty() || maxHourlyHours == 0) return List.of();

        ArrayList<PopView.HourlyPopSeries26.Point> points =
                new ArrayList<>(Math.min(raw.size(), maxHourlyHours));

        for (PopView.HourlyPopSeries26.Point p : raw) {
            if (p == null) continue;
            if (p.validAt() == null) break;
            points.add(p);
            if (points.size() == maxHourlyHours) break;
        }

        if (points.isEmpty()) return List.of();

        List<RainForecastParts.HourlyPart> parts = computeRainSegments(points);
        return parts.isEmpty() ? List.of() : List.copyOf(parts);
    }

    /** POP 임계치 이상이 연속되는 구간을 HourlyPart로 만듦
     *  - 간격 체크 없음(입력 points는 시간순 정렬되어 있다고 가정)
     *  - 구간의 끝은 "직전 시각(prevAt)" 포함                    */
    private List<RainForecastParts.HourlyPart> computeRainSegments(List<PopView.HourlyPopSeries26.Point> points) {
        List<RainForecastParts.HourlyPart> parts = new ArrayList<>();

        boolean inRain = false;
        LocalDateTime segStart = null;
        LocalDateTime prevAt = null;

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

    private List<RainForecastParts.DayPart> buildDayParts(PopView view) {
        List<PopView.DailyPopSeries7.DailyPop> days = view.daily().days();
        if (days == null || days.isEmpty()) return List.of();

        List<RainForecastParts.DayPart> parts = new ArrayList<>(days.size());

        // dayOffset 유지 (0~6)
        for (int dayOffset = 0; dayOffset < days.size(); dayOffset++) {
            PopView.DailyPopSeries7.DailyPop d = days.get(dayOffset);
            boolean am = d.am() >= rainThreshold;
            boolean pm = d.pm() >= rainThreshold;
            parts.add(new RainForecastParts.DayPart(dayOffset, am, pm));
        }

        return List.copyOf(parts);
    }
}