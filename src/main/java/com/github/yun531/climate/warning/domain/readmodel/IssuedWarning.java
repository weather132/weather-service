package com.github.yun531.climate.warning.domain.readmodel;

import com.github.yun531.climate.warning.domain.model.WarningKind;
import com.github.yun531.climate.warning.domain.model.WarningLevel;

import java.time.LocalDateTime;

public record IssuedWarning(
        String regionId,            // 지역 코드
        WarningKind kind,           // 호우 / 폭염 / 강풍 / 태풍 ...
        WarningLevel level,         // 예비특보 / 주의보 / 경보
        LocalDateTime updatedAt     // 특보가 발효되거나 갱신된 시각
) {}