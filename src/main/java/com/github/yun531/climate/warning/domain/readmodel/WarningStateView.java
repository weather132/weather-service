package com.github.yun531.climate.warning.domain.readmodel;

import com.github.yun531.climate.warning.domain.model.WarningKind;
import com.github.yun531.climate.warning.domain.model.WarningLevel;

import java.time.LocalDateTime;

public record WarningStateView(
        String regionId,
        WarningKind kind,
        WarningLevel level,
        LocalDateTime updatedAt
) {}