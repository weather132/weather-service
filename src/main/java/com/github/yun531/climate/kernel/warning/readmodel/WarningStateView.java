package com.github.yun531.climate.kernel.warning.readmodel;

import com.github.yun531.climate.kernel.warning.model.WarningKind;
import com.github.yun531.climate.kernel.warning.model.WarningLevel;

import java.time.LocalDateTime;

public record WarningStateView(
        String regionId,
        WarningKind kind,
        WarningLevel level,
        LocalDateTime updatedAt
) {}