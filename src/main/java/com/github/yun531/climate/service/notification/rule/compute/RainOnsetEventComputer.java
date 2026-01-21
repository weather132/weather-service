package com.github.yun531.climate.service.notification.rule.compute;

import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.notification.model.PopSeries24;
import com.github.yun531.climate.service.notification.model.PopSeriesPair;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PopSeriesPair(현재/이전 POP) -> "비 시작" 이벤트 목록 계산만 담당
 * - validAt(절대 시각) 기준으로 prev와 비교
 */
public class RainOnsetEventComputer {

    private final int rainThreshold;
    private final String srcRuleKey;
    private final String srcRuleName;
    private final String validAtKey;
    private final String popKey;
    private final int maxPoints;

    public RainOnsetEventComputer(int rainThreshold,
                                  String srcRuleKey,
                                  String srcRuleName,
                                  String validAtKey,
                                  String popKey) {
        this(rainThreshold, srcRuleKey, srcRuleName, validAtKey, popKey, PopSeries24.SIZE);
    }

    public RainOnsetEventComputer(int rainThreshold,
                                  String srcRuleKey,
                                  String srcRuleName,
                                  String validAtKey,
                                  String popKey,
                                  int maxPoints) {
        this.rainThreshold = rainThreshold;
        this.srcRuleKey = srcRuleKey;
        this.srcRuleName = srcRuleName;
        this.validAtKey = validAtKey;
        this.popKey = popKey;
        this.maxPoints = Math.max(1, maxPoints);
    }

    public List<AlertEvent> detect(String regionId, PopSeriesPair series, LocalDateTime computedAt) {
        if (series == null) return List.of();

        PopSeries24 cur = series.current();
        PopSeries24 prv = series.previous();
        if (cur == null || prv == null) return List.of();

        // prev: validAt -> pop
        Map<LocalDateTime, Integer> prevPopByValidAt = new HashMap<>();
        for (PopSeries24.Point p : prv.points()) {
            if (p == null || p.validAt() == null) continue;
            prevPopByValidAt.put(p.validAt(), p.pop());
        }

        // current: validAt 기준 정렬 후 maxPoints만
        List<PopSeries24.Point> curPoints =
                cur.points().stream()
                        .filter(p -> p != null && p.validAt() != null)
                        .sorted(Comparator.comparing(PopSeries24.Point::validAt))
                        .limit(maxPoints)
                        .toList();

        if (curPoints.isEmpty()) return List.of();

        List<AlertEvent> events = new ArrayList<>();

        for (PopSeries24.Point p : curPoints) {
            LocalDateTime at = p.validAt();
            int curPop = p.pop();

            boolean emit;
            Integer prevPop = prevPopByValidAt.get(at);
            if (prevPop != null) {
                // 비교 가능: 이전 비 아님 -> 현재 비
                emit = prevPop < rainThreshold && curPop >= rainThreshold;
            } else {
                // 비교 불가 구간: 현재 비면 발생(기존 정책의 "isRain" 대응)
                emit = curPop >= rainThreshold;
            }

            if (!emit) continue;

            events.add(createEvent(regionId, computedAt, at, curPop));
        }

        return events.isEmpty() ? List.of() : List.copyOf(events);
    }

    private AlertEvent createEvent(String regionId, LocalDateTime computedAt, LocalDateTime validAt, int pop) {
        Map<String, Object> payload = Map.of(
                srcRuleKey, srcRuleName,
                validAtKey, validAt.toString(), // "2026-01-14T21:00:00"
                popKey, pop
        );
        return new AlertEvent(AlertTypeEnum.RAIN_ONSET, regionId, computedAt, payload);
    }
}