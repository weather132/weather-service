package com.github.yun531.climate.notification.domain.rule.compute;

import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.notification.domain.readmodel.PopViewPair;

import java.time.LocalDateTime;
import java.util.*;

/** PopViewPair(현재/이전 POP) -> "비 시작" 이벤트 목록 계산만 담당
 * - validAt(절대 시각) 기준으로 prev와 비교                         */
public class RainOnsetEventComputer {

    public record Hit(LocalDateTime validAt, int pop) {}

    private final int rainThreshold;
    private final int maxPoints;

    public RainOnsetEventComputer(int rainThreshold, int maxPoints) {
        this.rainThreshold = rainThreshold;
        this.maxPoints = Math.max(1, maxPoints);
    }

    public List<Hit> detect(PopViewPair pair) {
        if (pair == null || pair.current() == null || pair.previous() == null) return List.of();

        PopView curView = pair.current();
        PopView prvView = pair.previous();

        // prev: validAt -> pop
        Map<LocalDateTime, Integer> prevPopByValidAt = new HashMap<>(PopView.HOURLY_SIZE * 2);
        for (PopView.HourlyPopSeries26.Point p : prvView.hourly().points()) {
            if (p == null) continue;
            LocalDateTime at = p.validAt();
            if (at == null) continue;
            prevPopByValidAt.put(at, p.pop());
        }

        List<Hit> out = new ArrayList<>(8);

        // PopView.HourlyPopSeries26는 fromHourlyPoints에서 validAt 정렬+패딩이 끝남
        List<PopView.HourlyPopSeries26.Point> points = curView.hourly().points();

        int seen = 0;
        for (int i = 0; i < points.size() && seen < maxPoints; i++) {
            PopView.HourlyPopSeries26.Point p = points.get(i);
            if (p == null) continue;

            LocalDateTime at = p.validAt();
            if (at == null) break; // 패딩 구간

            int curPop = p.pop();
            Integer prevPop = prevPopByValidAt.get(at);

            boolean emit;
            if (prevPop != null) {
                // 비교 가능: 이전 비 아님 -> 현재 비
                emit = prevPop < rainThreshold && curPop >= rainThreshold;
            } else {
                // 비교 불가 구간: 현재 비면 발생(기존 정책의 "isRain" 대응)
                emit = curPop >= rainThreshold;
            }

            if (emit) out.add(new Hit(at, curPop));
            seen++;
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
    }
}