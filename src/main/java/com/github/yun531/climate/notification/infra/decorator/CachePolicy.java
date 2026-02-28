package com.github.yun531.climate.notification.infra.decorator;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.rule.criteria.AlertCriteria;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 룰별 캐시 정책
 * - thresholdMinutes: 재계산/TTL 기준
 * - sinceForCache: RegionCache의 since 기반 판단에 넣을 기준 시각(예: criteria.since vs now)
 * - computedAt: CacheEntry에 기록할 computedAt(예: snapshot reportTime 기반 or now 기반)
 */
public interface CachePolicy {
    int thresholdMinutes();
    LocalDateTime sinceForCache(AlertCriteria criteria, LocalDateTime now);
    LocalDateTime computedAt(List<AlertEvent> events, LocalDateTime now);
}