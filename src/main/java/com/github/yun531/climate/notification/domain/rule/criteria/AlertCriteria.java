package com.github.yun531.climate.notification.domain.rule.criteria;

import com.github.yun531.climate.kernel.warning.model.WarningKind;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * 도메인 룰 평가에 필요한 "판단 기준"만 담는 값 객체.
 */
public record AlertCriteria(
        LocalDateTime since,                  // null 가능
        Set<WarningKind> filterWarningKinds,  // null/empty면 전체 허용
        Integer rainHourLimit                 // null 이면 제한 없음
) {}