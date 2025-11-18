package com.github.yun531.climate.service.rule;

import com.github.yun531.climate.domain.PopSeries24;
import com.github.yun531.climate.service.ClimateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
        import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class RainOnsetChangeRule implements AlertRule {

    private static final int RAIN_TH = RainThresholdEnum.RAIN.getThreshold();
    private static final long RECOMPUTE_THRESHOLD_MINUTES = 165L; // since 보다 165분 이상 오래됐으면 재계산

    private final ClimateService climateService;

    /** 지역별 캐시: 계산결과 + 계산시각 */
    private final Map<Long, CacheEntry> cache = new ConcurrentHashMap<>();
    private record CacheEntry(List<AlertEvent> events, Instant computedAt) {}

    @Override public AlertTypeEnum supports() { return AlertTypeEnum.RAIN_ONSET; }

    @Override
    public List<AlertEvent> evaluate(List<Long> regionIds, Instant since) {
        if (regionIds == null || regionIds.isEmpty()) return List.of();

        List<AlertEvent> out = new ArrayList<>();

        for (Long regionId : regionIds) {
            CacheEntry entry = cache.get(regionId);

            // 재계산 필요 여부 판정: 캐시 없음 OR since==null OR (computedAt < since - 40m)
            boolean needRecompute = (entry == null) || shouldRecompute(entry, since);

            if (needRecompute) {
                entry = computeForRegion(regionId);
                cache.put(regionId, entry);
            }

            List<AlertEvent> events = entry.events();
            if (events == null || events.isEmpty()) continue;

            out.addAll(events);
        }
        return out;
    }

    /** since == null 이면 무조건 재계산 OR computedAt > since + 165분 이면 재계산 */
    private boolean shouldRecompute(CacheEntry entry, Instant since) {
        if (since == null) return true;

        Instant computedAt = entry.computedAt();
        if (computedAt == null) return true;

        Instant floor = since.minus(RECOMPUTE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);
        return computedAt.isBefore(floor);
    }


    /** region 계산 수행 */
    private CacheEntry computeForRegion(Long regionId) {
        ClimateService.PopSeries series = climateService.loadDefaultPopSeries(regionId);
        if (series == null || series.current() == null || series.previous() == null) {
            return new CacheEntry(List.of(), Instant.now());
        }

        PopSeries24 cur = series.current();
        PopSeries24 prv = series.previous();
        int gap = series.reportTimeGap();      // 이전 스냅 → 현재 스냅까지 시간 차 (시간 단위)
        Instant computedAt = Instant.now();    // todo :  ?

        int curSize = cur.size();
        int prvSize = prv.size();

        int maxH = Math.min(curSize - 1, prvSize - 1 - gap);
        if (maxH < 0) {
            return new CacheEntry(List.of(), Instant.now());
        }

        List<AlertEvent> events = new ArrayList<>();
        for (int h = 0; h <= maxH; h++) {
            int prevIdx = h + gap;
            boolean wasNotRain = prv.get(prevIdx) < RAIN_TH;
            boolean nowRain    = cur.get(h)      >= RAIN_TH;

            if (wasNotRain && nowRain) {
                events.add(new AlertEvent(
                        AlertTypeEnum.RAIN_ONSET,
                        regionId,
                        computedAt,
                        Map.of(
                                "_srcRule", "RainOnsetChangeRule",
                                "hour", h,
                                "pop",  cur.get(h)
                        )
                ));
            }
        }
        return new CacheEntry(List.copyOf(events), computedAt);
    }

    /** 캐시 무효화 */
    public void invalidate(Long regionId) { cache.remove(regionId); }
    public void invalidateAll() { cache.clear(); }
}
