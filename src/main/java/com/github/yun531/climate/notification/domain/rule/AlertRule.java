package com.github.yun531.climate.notification.domain.rule;

import com.github.yun531.climate.notification.domain.model.AlertEvent;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.application.command.GenerateAlertsCommand;

import java.time.LocalDateTime;
import java.util.List;

public interface AlertRule {

    /** @return 해당 룰이 지원하는 AlertTypeEnum */
    AlertTypeEnum supports();

    default List<AlertEvent> evaluate(GenerateAlertsCommand command) {
        return evaluate(command, LocalDateTime.now());
    }

    List<AlertEvent> evaluate(GenerateAlertsCommand command, LocalDateTime now);
}