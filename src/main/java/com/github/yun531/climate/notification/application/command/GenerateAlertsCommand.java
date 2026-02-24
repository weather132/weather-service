package com.github.yun531.climate.notification.application.command;

import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;
import com.github.yun531.climate.notification.domain.model.WarningKind;
import org.springframework.lang.Nullable;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public record GenerateAlertsCommand(
        List<String> regionIds,
        @Nullable LocalDateTime since,
        Set<AlertTypeEnum> enabledTypes,
        @Nullable Set<WarningKind> filterWarningKinds,
        @Nullable Integer rainHourLimit
) {
    public GenerateAlertsCommand {
        enabledTypes = (enabledTypes == null || enabledTypes.isEmpty())
                ? EnumSet.noneOf(AlertTypeEnum.class)
                : EnumSet.copyOf(enabledTypes);
    }
}