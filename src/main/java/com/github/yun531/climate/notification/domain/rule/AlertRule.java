package com.github.yun531.climate.notification.domain.rule;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.rule.criteria.AlertCriteria;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 도메인 룰은 "지역 1개"에 대해서만 판단한다.
 * - 지역 리스트 순회, enabledTypes 필터링, 캐시/TTL, dedup/sort는 application/infra 책임.
 */
public interface AlertRule {

    /** @return 해당 룰이 지원하는 AlertTypeEnum */
    AlertTypeEnum supports();

    List<AlertEvent> evaluate(String regionId, AlertCriteria criteria, LocalDateTime now);
}