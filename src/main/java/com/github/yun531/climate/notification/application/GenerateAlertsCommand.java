package com.github.yun531.climate.notification.application;

import com.github.yun531.climate.warning.domain.model.WarningKind;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import org.springframework.lang.Nullable;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record GenerateAlertsCommand(
        List<String> regionIds,
        @Nullable Integer sinceHours,             // 유효한 기상 특보(발효시간)의 ttl
        Set<AlertTypeEnum> enabledTypes,
        @Nullable Set<WarningKind> warningKinds,
        @Nullable Integer rainHourLimit
) {
    public GenerateAlertsCommand {
        enabledTypes = (enabledTypes == null || enabledTypes.isEmpty())
                ? EnumSet.noneOf(AlertTypeEnum.class)
                : EnumSet.copyOf(enabledTypes);
    }
}