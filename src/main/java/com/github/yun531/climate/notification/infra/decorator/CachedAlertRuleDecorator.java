package com.github.yun531.climate.notification.infra.decorator;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.rule.criteria.AlertCriteria;
import com.github.yun531.climate.notification.domain.rule.AlertRule;
import com.github.yun531.climate.shared.cache.CacheEntry;
import com.github.yun531.climate.shared.cache.KeyCache;
import com.github.yun531.climate.shared.time.TimeUtil;

import java.time.LocalDateTime;
import java.util.List;

public class CachedAlertRuleDecorator implements AlertRule {

    private final AlertRule delegate;
    private final CachePolicy policy;

    private final KeyCache<List<AlertEvent>> cache = new KeyCache<>();

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
                ? TimeUtil.nowTruncatedToMinute()
                : TimeUtil.truncateToMinutes(now);

        LocalDateTime referenceTime = policy.referenceTimeForCache(criteria, effectiveNow);
        if (referenceTime != null) referenceTime = TimeUtil.truncateToMinutes(referenceTime);

        CacheEntry<List<AlertEvent>> entry = cache.getOrCompute(
                regionId,
                referenceTime,
                policy.thresholdMinutes(),
                () -> {
                    List<AlertEvent> computed = delegate.evaluate(regionId, criteria, effectiveNow);
                    List<AlertEvent> safe = (computed == null) ? List.of() : List.copyOf(computed);

                    LocalDateTime anchor = policy.computedAt(safe, effectiveNow);
                    anchor = (anchor == null) ? effectiveNow : TimeUtil.truncateToMinutes(anchor);

                    return new CacheEntry<>(safe, anchor);
                }
        );

        if (entry == null || entry.value() == null || entry.value().isEmpty()) return List.of();
        return entry.value();
    }
}