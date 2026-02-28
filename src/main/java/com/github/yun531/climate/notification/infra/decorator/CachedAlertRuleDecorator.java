package com.github.yun531.climate.notification.infra.decorator;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.rule.criteria.AlertCriteria;
import com.github.yun531.climate.notification.domain.rule.AlertRule;
import com.github.yun531.climate.shared.cache.CacheEntry;
import com.github.yun531.climate.shared.cache.RegionCache;
import com.github.yun531.climate.shared.time.TimeUtil;

import java.time.LocalDateTime;
import java.util.List;

public class CachedAlertRuleDecorator implements AlertRule {

    private final AlertRule delegate;
    private final CachePolicy policy;

    private final RegionCache<List<AlertEvent>> cache = new RegionCache<>();

    public CachedAlertRuleDecorator(AlertRule delegate, CachePolicy policy) {
        this.delegate = delegate;
        this.policy = policy;
    }

    @Override
    public AlertTypeEnum supports() {
        return delegate.supports();
    }

    @Override
    public List<AlertEvent> evaluate(String regionId, AlertCriteria criteria, LocalDateTime now) {
        if (regionId == null || regionId.isBlank()) return List.of();

        LocalDateTime effectiveNow = (now == null)
                ? TimeUtil.nowMinutes()
                : TimeUtil.truncateToMinutes(now);

        LocalDateTime since = policy.sinceForCache(criteria, effectiveNow);
        since = (since == null) ? null : TimeUtil.truncateToMinutes(since);

        CacheEntry<List<AlertEvent>> entry = cache.getOrComputeSinceBased(
                regionId,
                since,
                policy.thresholdMinutes(),
                () -> {
                    List<AlertEvent> computed = delegate.evaluate(regionId, criteria, effectiveNow);
                    List<AlertEvent> safe = (computed == null) ? List.of() : List.copyOf(computed);

                    LocalDateTime computedAt = policy.computedAt(safe, effectiveNow);
                    computedAt = (computedAt == null) ? effectiveNow : TimeUtil.truncateToMinutes(computedAt);

                    return new CacheEntry<>(safe, computedAt);
                }
        );

        if (entry == null || entry.value() == null || entry.value().isEmpty()) return List.of();
        return entry.value();
    }
}