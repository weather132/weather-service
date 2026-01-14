package com.github.yun531.climate.service.notification.rule.compute;

import com.github.yun531.climate.service.notification.model.PopDailySeries7;
import com.github.yun531.climate.service.notification.model.PopForecastSeries;
import com.github.yun531.climate.service.notification.model.PopSeries24;
import com.github.yun531.climate.service.notification.model.RainForecastParts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * PopForecastSeries -> RainForecastParts 계산만 담당
 */
public class RainForecastComputer {
    private static final Logger log = LoggerFactory.getLogger(RainForecastComputer.class);

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
        if (hourly == null || hourly.getPoints() == null || hourly.getPoints().isEmpty()) {
            return List.of();
        }

        // validAt 기준 정렬 (null은 뒤로)
        List<PopSeries24.Point> sorted =
                hourly.getPoints().stream()
                        .sorted(Comparator.comparing(
                                PopSeries24.Point::validAt,
                                Comparator.nullsLast(Comparator.naturalOrder())
                        ))
                        .toList();

        // validAt 없는 포인트는 시간구간 계산 불가 → 제외
        int maxCount = Math.max(0, Math.min(maxHourlyHours, sorted.size()));
        List<PopSeries24.Point> points =
                sorted.stream()
                        .filter(p -> p.validAt() != null)
                        .limit(maxCount)
                        .toList();

        if (points.isEmpty()) return List.of();

        // POP 임계치 연속 구간 계산 (간격 체크 없음)
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
                    // 직전 시각(prevAt)까지 포함
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

        /***
        // ---- 계산 완료된 hourlyParts 로그 ----
        if (!parts.isEmpty()) {
            // regionId가 필요하면 PopForecastSeries/PopSeries24 쪽에서 꺼낼 수 있는지 확인 필요
            log.info("RainForecastComputer hourlyParts computed: count={}, threshold={}, maxCountUsed={}",
                    parts.size(), rainThreshold, maxCount);

            for (int i = 0; i < parts.size(); i++) {
                RainForecastParts.HourlyPart hp = parts.get(i);
                // HourlyPart 필드명이 start()/end()가 아니면 여기만 맞춰 변경
                log.info("  hourlyPart[{}]: start={}, end={}", i, hp.start(), hp.end());
            }
        } else {
            log.info("RainForecastComputer hourlyParts computed: empty (threshold={}, maxCountUsed={})",
                    rainThreshold, maxCount);
        }
        **/

        return parts.isEmpty() ? List.of() : List.copyOf(parts);
    }

    private List<RainForecastParts.DayPart> buildDayParts(PopForecastSeries fs) {
        PopDailySeries7 daily = fs.daily();
        if (daily == null || daily.days() == null || daily.days().isEmpty()) return List.of();

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