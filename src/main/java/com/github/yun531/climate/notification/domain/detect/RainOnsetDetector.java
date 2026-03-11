package com.github.yun531.climate.notification.domain.detect;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.payload.RainOnsetPayload;
import com.github.yun531.climate.notification.domain.readmodel.PopView;
import com.github.yun531.climate.shared.time.TimeUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PopView.Pair(현재/이전 POP) -> "비 시작" AlertEvent 목록 계산.
 */
public class RainOnsetDetector {

    private final int rainThreshold;
    private final int maxHourlyPoints;

    public RainOnsetDetector(int rainThreshold, int maxHourlyPoints) {
        this.rainThreshold = rainThreshold;
        this.maxHourlyPoints = Math.max(1, maxHourlyPoints);
    }

    /**
     * @return 비 예보시각 AlertEvents 목록 (빈 리스트 가능)
     */
    public List<AlertEvent> detect(String regionId, PopView.Pair pair, LocalDateTime now) {
        if (regionId == null || regionId.isBlank()) return List.of();
        if (pair == null || pair.current() == null || pair.previous() == null) return List.of();
        if (now == null) return List.of();

        PopView curView = pair.current();
        LocalDateTime computedAt = TimeUtil.truncateToMinutes(
                curView.reportTime() != null ? curView.reportTime() : now);
        Map<LocalDateTime, Integer> prevPopMap = buildPrevPopMap(pair.previous());

        // cur 순회하면서 비 시작 감지
        List<AlertEvent> out = new ArrayList<>(8);
        List<PopView.HourlySeries.Point> points = curView.hourly().points();

        // maxHourlyPoints는 리스트 인덱스(i)가 아니라, 실제 처리한 "유효 포인트"(seen) 기준으로 제한
        int seen = 0;
        for (int i = 0; i < points.size() && seen < maxHourlyPoints; i++) {
            PopView.HourlySeries.Point p = points.get(i);
            if (p == null) continue;

            LocalDateTime at = p.validAt();
            if (at == null) break;

            Integer curPop = p.pop();
            if (curPop == null) { seen++; continue; }

            // 이전 예보의 같은 validAt 시각 POP과 비교(예보 업데이트 전후 변화 감지)
            if (isOnset(curPop, prevPopMap.get(at))) {
                RainOnsetPayload payload = new RainOnsetPayload(
                        AlertTypeEnum.RAIN_ONSET, at, curPop
                );
                out.add(new AlertEvent(AlertTypeEnum.RAIN_ONSET, regionId, computedAt, payload));
            }
            seen++;
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    /** 이전에 비 아님 → 현재 비 = onset. 비교 불가(prev 없음)면 현재 비 여부만 판단 */
    private boolean isOnset(int curPop, Integer prevPop) {
        if (prevPop != null) {
            return prevPop < rainThreshold && curPop >= rainThreshold;
        }
        return curPop >= rainThreshold;
    }

    private Map<LocalDateTime, Integer> buildPrevPopMap(PopView prvView) {
        Map<LocalDateTime, Integer> map = new HashMap<>(PopView.HOURLY_SIZE * 2);

        for (PopView.HourlySeries.Point p : prvView.hourly().points()) {
            if (p == null) continue;

            LocalDateTime at = p.validAt();
            if (at == null) continue;

            Integer pop = p.pop();
            if (pop != null) map.put(at, pop);
        }

        return map;
    }
}