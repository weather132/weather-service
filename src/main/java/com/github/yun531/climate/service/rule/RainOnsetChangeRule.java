package com.github.yun531.climate.service.rule;

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
    private static final long RECOMPUTE_THRESHOLD_MINUTES = 40L; // since 보다 40분 이상 오래됐으면 재계산

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
            // 캐시 조회
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

    /** since == null 이면 무조건 재계산 OR computedAt > since + 40분 이면 재계산 */
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
        if (series == null || series.current() == null || series.previousShifted() == null) {
            return new CacheEntry(List.of(), Instant.now());
        }

        int[] cur = series.current();              // 24개 (0~23시)
        int[] prvShift = series.previousShifted(); // 23개 (0~22 비교용)
        Instant computedAt = Instant.now();    // todo :  ?

        List<AlertEvent> events = new ArrayList<>(4);
        for (int h = 0; h <= 22; h++) {
            boolean wasNotRain = prvShift[h] < RAIN_TH;
            boolean nowRain    = cur[h] >= RAIN_TH;
            if (wasNotRain && nowRain) {
                events.add(new AlertEvent(
                        AlertTypeEnum.RAIN_ONSET,
                        regionId,
                        computedAt,
                        Map.of(
                                "_srcRule", "RainOnsetChangeRule",
                                "hour", h,
                                "pop",  cur[h]
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
