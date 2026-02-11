package com.github.yun531.climate.service.notification.rule;

import com.github.yun531.climate.service.notification.dto.NotificationRequest;
import com.github.yun531.climate.service.notification.model.AlertEvent;
import com.github.yun531.climate.service.notification.model.AlertTypeEnum;

import java.time.LocalDateTime;
import java.util.List;

public interface AlertRule {

    /** @return 해당 룰이 지원하는 AlertTypeEnum */
    AlertTypeEnum supports();

    /**
     * NotificationRequest 기반 평가.
     * - regionIds, since, filterWarningKinds, rainHourLimit 등은
     *   각 룰이 필요한 것만 골라 사용한다.
     */
    List<AlertEvent> evaluate(NotificationRequest request); //todo : rule 수정 후 제거

    /**
     * 신규 시그니처: now 주입 가능(결정적 실행)
     * - 아직 마이그레이션 안 된 룰은 기본 구현이 evaluate(request)로 fallback
     */
    default List<AlertEvent> evaluate(NotificationRequest request, LocalDateTime now) {
        return evaluate(request);
    }
}