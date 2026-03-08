package com.github.yun531.climate.notification.domain.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.yun531.climate.kernel.warning.model.WarningKind;
import com.github.yun531.climate.kernel.warning.model.WarningLevel;
import com.github.yun531.climate.notification.domain.model.AlertTypeEnum;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WarningIssuedPayload(
        AlertTypeEnum type,
        WarningKind kind,
        WarningLevel level
) implements AlertPayload {

    @Override
    public Map<String, String> toFcmData() {
        return Map.of(
                "_source", type == null ? "" : type.source(),
                "kind", kind == null ? "" : kind.name(),
                "level", level == null ? "" : level.name()
        );
    }
}