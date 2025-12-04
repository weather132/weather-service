package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.service.notification.NotificationRequest;

import java.util.List;

public interface AlertRule {

    /** @return 해당 룰이 지원하는 AlertTypeEnum */
    AlertTypeEnum supports();

    /**
     * NotificationRequest 기반 평가.
     * - regionIds, since, filterWarningKinds, rainHourLimit 등은
     *   각 룰이 필요한 것만 골라 사용한다.
     */
    List<AlertEvent> evaluate(NotificationRequest request);
}