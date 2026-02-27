package com.github.yun531.climate.notification.domain.readmodel;

import com.github.yun531.climate.notification.domain.model.WarningKind;
import com.github.yun531.climate.notification.domain.model.WarningLevel;

import java.time.LocalDateTime;

public record WarningStateView(
        String regionId,
        WarningKind kind,
        WarningLevel level,
        LocalDateTime updatedAt
) {}