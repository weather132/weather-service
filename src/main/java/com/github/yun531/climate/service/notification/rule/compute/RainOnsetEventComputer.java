package com.github.yun531.climate.service.notification.rule.compute;

import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;
import com.github.yun531.climate.service.notification.model.PopView;
import com.github.yun531.climate.service.notification.model.PopViewPair;
import com.github.yun531.climate.service.notification.model.payload.RainOnsetPayload;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** PopViewPair(현재/이전 POP) -> "비 시작" 이벤트 목록 계산만 담당
 * - validAt(절대 시각) 기준으로 prev와 비교                         */
public class RainOnsetEventComputer {

    private final int rainThreshold;
    private final String srcRuleName;
    private final int maxPoints;

    public RainOnsetEventComputer(int rainThreshold, String srcRuleName) {
        this(rainThreshold, srcRuleName, PopView.HOURLY_SIZE);
    }

    public RainOnsetEventComputer(int rainThreshold, String srcRuleName, int maxPoints) {
        this.rainThreshold = rainThreshold;
        this.srcRuleName = srcRuleName;
        this.maxPoints = Math.max(1, maxPoints);
    }

    public List<AlertEvent> detect(String regionId, PopViewPair pair, LocalDateTime occurredAt) {
        if (pair == null) return List.of();

        PopView curView = pair.current();
        PopView prvView = pair.previous();
        if (curView == null || prvView == null) return List.of();

        // prev: validAt -> pop
        Map<LocalDateTime, Integer> prevPopByValidAt = new HashMap<>();
        for (PopView.HourlyPopSeries26.Point p : prvView.hourly().points()) {
            if (p == null || p.validAt() == null) continue;
            prevPopByValidAt.put(p.validAt(), p.pop());
        }

        // current: validAt 기준 정렬 후 maxPoints만
        List<PopView.HourlyPopSeries26.Point> curPoints =
                curView.hourly().points().stream()
                        .filter(p -> p != null && p.validAt() != null)
                        .sorted(Comparator.comparing(PopView.HourlyPopSeries26.Point::validAt))
                        .limit(maxPoints)
                        .toList();

        if (curPoints.isEmpty()) return List.of();

        List<AlertEvent> events = new ArrayList<>();

        for (PopView.HourlyPopSeries26.Point p : curPoints) {
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

            events.add(createEvent(regionId, occurredAt, at, curPop));
        }

        return events.isEmpty() ? List.of() : List.copyOf(events);
    }

    private AlertEvent createEvent(String regionId, LocalDateTime occurredAt, LocalDateTime validAt, int pop) {
        // Map payload 대신 typed payload
        RainOnsetPayload payload = new RainOnsetPayload(srcRuleName, validAt, pop);
        return new AlertEvent(AlertTypeEnum.RAIN_ONSET, regionId, occurredAt, payload);
    }
}