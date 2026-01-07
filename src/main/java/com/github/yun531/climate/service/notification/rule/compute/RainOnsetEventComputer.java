package com.github.yun531.climate.service.notification.rule.compute;

import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.notification.model.PopSeries24;
import com.github.yun531.climate.service.notification.model.PopSeriesPair;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PopSeriesPair(현재/이전 POP) -> "비 시작" 이벤트 목록 계산만 담당
 */
public class RainOnsetEventComputer {

    private final int rainThreshold;
    private final String srcRuleKey;
    private final String srcRuleName;
    private final String hourKey;
    private final String popKey;

    public RainOnsetEventComputer(int rainThreshold,
                                  String srcRuleKey,
                                  String srcRuleName,
                                  String hourKey,
                                  String popKey) {
        this.rainThreshold = rainThreshold;
        this.srcRuleKey = srcRuleKey;
        this.srcRuleName = srcRuleName;
        this.hourKey = hourKey;
        this.popKey = popKey;
    }

    public List<AlertEvent> detect(String regionId, PopSeriesPair series, LocalDateTime computedAt) {
        PopSeries24 cur = series.current();
        PopSeries24 prv = series.previous();
        int gapHours = series.reportTimeGap();

        int lastComparableHour = computeMaxComparableHour(cur, prv, gapHours);
        if (lastComparableHour < 0) return List.of();

        List<AlertEvent> events = new ArrayList<>();

        for (int hour = 1; hour <= 24; hour++) {
            boolean emit;
            if (hour <= lastComparableHour) {
                emit = isRainOnset(cur, prv, gapHours, hour);
            } else {
                emit = isRain(cur, hour);
            }

            if (!emit) continue;

            int pop = cur.get(hour);
            events.add(createEvent(regionId, computedAt, hour, pop));
        }

        return events.isEmpty() ? List.of() : List.copyOf(events);
    }

    private int computeMaxComparableHour(PopSeries24 cur, PopSeries24 prv, int gapHours) {
        if (gapHours < 0) return -1;

        int curLimit = cur.size() - 1;
        int prvLimit = prv.size() - 1 - gapHours;
        if (curLimit < 0 || prvLimit < 0) return -1;

        return Math.min(curLimit, prvLimit);
    }

    private boolean isRainOnset(PopSeries24 cur, PopSeries24 prv, int gapHours, int hour) {
        int prevIdx = hour + gapHours;
        int prevPop = prv.get(prevIdx);
        int curPop = cur.get(hour);
        return prevPop < rainThreshold && curPop >= rainThreshold;
    }

    private boolean isRain(PopSeries24 series, int hour) {
        return series.get(hour) >= rainThreshold;
    }

    private AlertEvent createEvent(String regionId, LocalDateTime computedAt, int hour, int pop) {
        Map<String, Object> payload = Map.of(
                srcRuleKey, srcRuleName,
                hourKey, hour,
                popKey, pop
        );
        return new AlertEvent(AlertTypeEnum.RAIN_ONSET, regionId, computedAt, payload);
    }
}