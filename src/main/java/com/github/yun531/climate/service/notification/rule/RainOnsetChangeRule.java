package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.dto.PopSeries;
import com.github.yun531.climate.dto.PopSeries24;
import com.github.yun531.climate.service.ClimateService;
import com.github.yun531.climate.service.notification.NotificationRequest;
import com.github.yun531.climate.util.CacheEntry;
import com.github.yun531.climate.util.RegionCache;
import io.micrometer.common.lang.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RainOnsetChangeRule implements AlertRule {

    private static final int RAIN_TH = RainThresholdEnum.RAIN.getThreshold();
    /** since 기준으로 해당 분(시간) 보다 오래된 계산 결과면 재계산 */
    private static final int RECOMPUTE_THRESHOLD_MINUTES = 165;
    /** payload key 상수 */
    private static final String PAYLOAD_SRC_RULE_KEY  = "_srcRule";
    private static final String PAYLOAD_SRC_RULE_NAME = "RainOnsetChangeRule";
    private static final String PAYLOAD_HOUR_KEY      = "hour";
    private static final String PAYLOAD_POP_KEY       = "pop";

    private final ClimateService climateService;

    /** 지역별 캐시: 계산결과 + 계산시각 */
    private final RegionCache<List<AlertEvent>> cache = new RegionCache<>();

    @Override
    public AlertTypeEnum supports() {
        return AlertTypeEnum.RAIN_ONSET;
    }

    @Override
    public List<AlertEvent> evaluate(NotificationRequest request) {
        List<Integer> regionIds = request.regionIds();
        LocalDateTime since     = request.since();
        Integer maxHour         = request.rainHourLimit(); // null이면 전체 시간대

        return evaluateInternal(regionIds, since, maxHour);
    }

    /**
     * maxHour 까지의 비 시작 알림만 반환.
     * - maxHour == null 이면 전체 시간대 반환
     */
    private List<AlertEvent> evaluateInternal(List<Integer> regionIds,
                                              LocalDateTime since,
                                              Integer maxHour) {
        if (regionIds == null || regionIds.isEmpty()) {
            return List.of();
        }

        List<AlertEvent> result = new ArrayList<>();

        for (int regionId : regionIds) {
            CacheEntry<List<AlertEvent>> entry =
                    cache.getOrComputeSinceBased(
                            regionId,
                            since,
                            RECOMPUTE_THRESHOLD_MINUTES,
                            () -> computeForRegion(regionId)
                    );

            List<AlertEvent> eventsForRegion = extractEvents(entry);
            if (eventsForRegion.isEmpty()) {
                continue;
            }

            if (maxHour != null) {
                eventsForRegion = filterByMaxHour(eventsForRegion, maxHour);
                if (eventsForRegion.isEmpty()) {
                    continue;
                }
            }

            result.addAll(eventsForRegion);
        }

        return result;
    }

    /**
     * CacheEntry에서 이벤트 리스트를 꺼낸다.
     * entry가 null이거나, value(List<AlertEvent>)가 null 또는 비어 있으면 빈 리스트를 반환한다.
     */
    private List<AlertEvent> extractEvents(@Nullable CacheEntry<List<AlertEvent>> entry) {
        if (entry == null || entry.value() == null || entry.value().isEmpty()) {
            return List.of();
        }
        return entry.value();
    }

    /** hour <= maxHour 인 이벤트만 남김  */
    private List<AlertEvent> filterByMaxHour(List<AlertEvent> events,
                                             int maxHourInclusive) {
        List<AlertEvent> filtered = new ArrayList<>();

        for (AlertEvent event : events) {
            Integer hour = extractHour(event);
            if (hour == null || hour <= maxHourInclusive) {
                filtered.add(event);
            }
        }
        return filtered;
    }

    @Nullable
    private Integer extractHour(AlertEvent event) {
        Map<String, Object> payload = event.payload();
        if (payload == null) {
            return null;
        }

        Object hourObj = payload.get(PAYLOAD_HOUR_KEY);
        if (hourObj instanceof Integer h) {
            return h;
        }
        if (hourObj instanceof Number n) {
            return n.intValue();
        }
        return null;
    }

    // 한 지역에 대한 비 시작 시점 계산
    private CacheEntry<List<AlertEvent>> computeForRegion(int regionId) {
        PopSeries series = climateService.loadDefaultPopSeries(regionId);

        if (!isValidSeries(series)) {
            return createEmptyCacheEntry();
        }

        LocalDateTime computedAt = series.curReportTime();
        List<AlertEvent> events  = detectRainOnsetEvents(regionId, series, computedAt);

        return new CacheEntry<>(List.copyOf(events), computedAt);
    }

    private boolean isValidSeries(PopSeries series) {
        return series != null
                && series.current() != null
                && series.previous() != null;
    }

    private CacheEntry<List<AlertEvent>> createEmptyCacheEntry() {
        return new CacheEntry<>(List.of(), null);
    }

    /** 시계열 비교 및 이벤트 생성 */
    private List<AlertEvent> detectRainOnsetEvents(int regionId,
                                                   PopSeries series,
                                                   LocalDateTime computedAt) {

        PopSeries24 cur = series.current();
        PopSeries24 prv = series.previous();
        int gapHours    = series.reportTimeGap();  // 이전 스냅 → 현재 스냅까지 시간 차 (시간 단위)

        int lastComparableHour = computeMaxComparableHour(cur, prv, gapHours);
        if (lastComparableHour < 0) {
            return List.of();
        }

        List<AlertEvent> events = new ArrayList<>();
        int lastHour = cur.size() - 1;

        for (int hour = 0; hour <= lastHour; hour++) {
            if (hour <= lastComparableHour) {
                // 이전 스냅과 비교 가능한 구간: “새로 시작한 비”만 감지
                if (isRainOnset(cur, prv, gapHours, hour)) {
                    int pop = cur.get(hour);
                    events.add(createRainOnsetEvent(regionId, computedAt, hour, pop));
                }
            } else {
                // maxH 이후 구간: 현재(cur)에서 비면 전부 이벤트로 간주
                if (isRain(cur, hour)) {
                    int pop = cur.get(hour);
                    events.add(createRainOnsetEvent(regionId, computedAt, hour, pop));
                }
            }
        }

        return events;
    }

    /** 현재/이전 시계열과 gap 으로 비교 가능한 최대 시간 인덱스 계산 */
    private int computeMaxComparableHour(PopSeries24 cur,
                                         PopSeries24 prv,
                                         int gapHours ) {
        if (gapHours  < 0) return -1;

        int curLimit = cur.size() - 1;
        int prvLimit = prv.size() - 1 - gapHours;
        if (curLimit < 0 || prvLimit < 0) {
            return -1;
        }

        return Math.min(curLimit, prvLimit);
    }

    /** h 시점에서 “비가 새로 시작했는지” 여부 판단 */
    private boolean isRainOnset(PopSeries24 cur,
                                PopSeries24 prv,
                                int gapHours,
                                int hour) {

        int prevIdx  = hour + gapHours;
        int prevPop  = prv.get(prevIdx);
        int curPop   = cur.get(hour);

        boolean wasNotRain = prevPop < RAIN_TH;
        boolean nowRain    = curPop  >= RAIN_TH;

        return wasNotRain && nowRain;
    }

    /** 현재 시계열에서 h 시점에 비라고 볼 수 있는지 여부 */
    private boolean isRain(PopSeries24 series, int hour) {
        int pop = series.get(hour);
        return pop >= RAIN_TH;
    }

    private AlertEvent createRainOnsetEvent(int regionId,
                                            LocalDateTime computedAt,
                                            int hour,
                                            int pop) {
        Map<String, Object> payload = Map.of(
                PAYLOAD_SRC_RULE_KEY, PAYLOAD_SRC_RULE_NAME,
                PAYLOAD_HOUR_KEY,     hour,
                PAYLOAD_POP_KEY,      pop
        );
        return new AlertEvent(
                AlertTypeEnum.RAIN_ONSET,
                regionId,
                computedAt,
                payload
        );
    }

    /** 캐시 무효화 */
    public void invalidate(int regionId) { cache.invalidate(regionId); }
    public void invalidateAll() { cache.invalidateAll(); }
}