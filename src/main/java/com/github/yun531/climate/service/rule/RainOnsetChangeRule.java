package com.github.yun531.climate.service.rule;

import com.github.yun531.climate.dto.PopSeries;
import com.github.yun531.climate.dto.PopSeries24;
import com.github.yun531.climate.service.ClimateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RainOnsetChangeRule implements AlertRule {
    private static final int RAIN_TH = RainThresholdEnum.RAIN.getThreshold();
    /** since 기준으로 해당 분(시간) 보다 오래된 계산 결과면 재계산 */
    private static final int RECOMPUTE_THRESHOLD_MINUTES = 165;

    private final ClimateService climateService;

    /** 지역별 캐시: 계산결과 + 계산시각 */
    private final Map<Integer, CacheEntry> cache = new ConcurrentHashMap<>();
    private record CacheEntry(
            List<AlertEvent> events,
            LocalDateTime computedAt
    ) {}

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.RAIN_ONSET;
    }

    @Override
    public List<AlertEvent> evaluate(List<Integer> regionIds, LocalDateTime since) {
        if (regionIds == null || regionIds.isEmpty()) {
            return List.of();
        }

        List<AlertEvent> out = new ArrayList<>();

        for (int regionId : regionIds) {
            CacheEntry entry = getOrComputeEntry(regionId, since);
            if (entry == null) continue;

            List<AlertEvent> events = entry.events();
            if (events == null || events.isEmpty()) continue;

            out.addAll(events);
        }
        return out;
    }

    // 캐시 조회/재계산
    private CacheEntry getOrComputeEntry(int regionId, LocalDateTime since) {
        CacheEntry entry = cache.get(regionId);

        if (needsRecompute(entry, since)) {
            entry = computeForRegion(regionId);
            cache.put(regionId, entry);
        }
        return entry;
    }

    /**
     * since == null 이면 무조건 재계산,
     * 아니면 since - RECOMPUTE_THRESHOLD_MINUTES 보다 computedAt 가 이전이면 재계산
     */
    private boolean needsRecompute(CacheEntry entry, LocalDateTime since) {
        if (since == null) return true;
        if (entry == null || entry.computedAt() == null) return true;

        LocalDateTime floor = since.minusMinutes(RECOMPUTE_THRESHOLD_MINUTES);
        return entry.computedAt().isBefore(floor);
    }

    // 한 지역에 대한 비 시작 시점 계산
    private CacheEntry computeForRegion(int regionId) {
        PopSeries series = climateService.loadDefaultPopSeries(regionId);

        if (!isValidSeries(series)) {
            return createEmptyCacheEntry();
        }

        LocalDateTime computedAt = series.curReportTime();
        List<AlertEvent> events = detectRainOnsetEvents(regionId, series, computedAt);

        return new CacheEntry(List.copyOf(events), computedAt);
    }

    private boolean isValidSeries(PopSeries series) {
        return series != null
                && series.current() != null
                && series.previous() != null;
    }

    private CacheEntry createEmptyCacheEntry() {
        return new CacheEntry(List.of(), LocalDateTime.now().withNano(0));
    }

    // 시계열 비교 및 이벤트 생성
    private List<AlertEvent> detectRainOnsetEvents(int regionId,
                                                   PopSeries series,
                                                   LocalDateTime computedAt) {

        PopSeries24 cur = series.current();
        PopSeries24 prv = series.previous();
        int gap = series.reportTimeGap();  // 이전 스냅 → 현재 스냅까지 시간 차 (시간 단위)

        int maxH = computeMaxComparableHour(cur, prv, gap);
        if (maxH < 0) {
            return List.of();
        }

        List<AlertEvent> events = new ArrayList<>();
        for (int h = 0; h <= maxH; h++) {
            if (isRainOnset(cur, prv, gap, h)) {
                events.add(createRainOnsetEvent(regionId, computedAt, h, cur.get(h)));
            }
        }
        return events;
    }

    /** 현재/이전 시계열과 gap 으로 비교 가능한 최대 시간 인덱스 계산 */
    private int computeMaxComparableHour(PopSeries24 cur, PopSeries24 prv, int gap) {
        if (gap < 0) return -1;

        int curSize = cur.size();
        int prvSize = prv.size();
        if (curSize <= 0 || prvSize <= 0) return -1;

        return Math.min(curSize - 1, prvSize - 1 - gap);
    }

    /** h 시점에서 “비가 새로 시작했는지” 여부 판단 */
    private boolean isRainOnset(PopSeries24 cur, PopSeries24 prv, int gap, int h) {
        int prevIdx = h + gap;
        int prevPop = prv.get(prevIdx);
        int curPop  = cur.get(h);

        boolean wasNotRain = prevPop < RAIN_TH;
        boolean nowRain    = curPop  >= RAIN_TH;

        System.out.println("h > wasNotRain: " + wasNotRain + ", nowRain: " + nowRain);
        return wasNotRain && nowRain;
    }

    private AlertEvent createRainOnsetEvent(int regionId,
                                            LocalDateTime computedAt,
                                            int hour,
                                            int pop) {
        Map<String, Object> payload = Map.of(
                "_srcRule", "RainOnsetChangeRule",
                "hour", hour,
                "pop",  pop
        );
        System.out.println("hour: " + hour + ", pop: " + pop);
        return new AlertEvent(
                AlertTypeEnum.RAIN_ONSET,
                regionId,
                computedAt,
                payload
        );
    }

    /** 캐시 무효화 */
    public void invalidate(int regionId) { cache.remove(regionId); }
    public void invalidateAll() { cache.clear(); }
}
