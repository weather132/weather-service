package com.github.yun531.climate.notification.infra.decorator;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.rule.criteria.AlertCriteria;

import java.time.LocalDateTime;
import java.util.List;

public record SinceBasedCachePolicy(int thresholdMinutes) implements CachePolicy {

    @Override
    public LocalDateTime sinceForCache(AlertCriteria criteria, LocalDateTime now) {
        return (criteria == null) ? null : criteria.since();
    }

    @Override
    public LocalDateTime computedAt(List<AlertEvent> events, LocalDateTime now) {
        if (events == null || events.isEmpty()) return now;

        AlertEvent first = events.get(0);
        LocalDateTime t = (first == null) ? null : first.occurredAt();
        return (t == null) ? now : t;
    }
}