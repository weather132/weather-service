package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.util.cache.CacheEntry;
import com.github.yun531.climate.util.cache.RegionCache;
import com.github.yun531.climate.util.time.TimeUtil;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.*;

import static com.github.yun531.climate.util.time.TimeUtil.nowMinutes;

public abstract class AbstractCachedRegionAlertRule<V> implements AlertRule {

    protected final RegionCache<V> cache = new RegionCache<>();

    /** getOrComputeSinceBased의 threshold/TTL(분) */
    protected abstract int thresholdMinutes();

    /**
     * 캐시 판단에 넣을 since.
     * - 기본: request.since()
     * - WarningIssuedRule 처럼 TTL을 "지금(now)" 기준으로 굴리고 싶으면 override 해서 now 반환
     */
    @Nullable
    protected LocalDateTime sinceForCache(NotificationRequest request, LocalDateTime now) {
        return request.since();
    }

    /** region 1개에 대한 계산(캐시 미스/재계산 시 호출) */
    protected abstract CacheEntry<V> computeForRegion(String regionId, LocalDateTime now);

    /** 캐시값(value)과 시간정보로 최종 AlertEvent 리스트 생성/보정 */
    protected abstract List<AlertEvent> buildEvents(String regionId,
                                                    V value,
                                                    @Nullable LocalDateTime computedAt,
                                                    LocalDateTime now,
                                                    NotificationRequest request
    );

    /** 값이 "비어있음"으로 간주되는지(리스트/맵 등 공통 처리) */
    protected boolean isEmptyValue(@Nullable V value) {
        if (value == null) return true;
        if (value instanceof Collection<?> c) return c.isEmpty();
        if (value instanceof Map<?, ?> m) return m.isEmpty();
        return false;
    }

    /** todo: 정리해야함, 레거시 호환: 내부에서 now 생성 */
    @Override
    public final List<AlertEvent> evaluate(NotificationRequest request) {
        return evaluate(request, nowMinutes());
    }

    /** NotificationService가 내려준 now 사용 */
    @Override
    public List<AlertEvent> evaluate(NotificationRequest request, LocalDateTime now) {
        List<String> regionIds = request.regionIds();
        if (regionIds == null || regionIds.isEmpty()) return List.of();

        LocalDateTime effectiveNow = (now == null) ? nowMinutes() : TimeUtil.truncateToMinutes(now);

        LocalDateTime since = sinceForCache(request, effectiveNow);
        since = (since == null) ? null : TimeUtil.truncateToMinutes(since);

        List<AlertEvent> out = new ArrayList<>();
        for (String regionId : regionIds) {
            CacheEntry<V> entry = cache.getOrComputeSinceBased(regionId,
                                                                since,
                                                                thresholdMinutes(),
                                                                () -> computeForRegion(regionId, effectiveNow)
            );

            if (entry == null || isEmptyValue(entry.value())) continue;

            List<AlertEvent> events = buildEvents(regionId,
                                                    entry.value(),
                                                    entry.computedAt(),
                                                    effectiveNow,
                                                    request
            );

            if (events != null && !events.isEmpty()) out.addAll(events);
        }

        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    public void invalidate(String regionId) {
        cache.invalidate(regionId);
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }
}