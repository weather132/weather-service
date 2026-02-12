package com.github.yun531.climate.service.notification.model.payload;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.yun531.climate.service.notification.model.WarningKind;
import com.github.yun531.climate.service.notification.model.WarningLevel;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record WarningIssuedPayload(
        String srcRule,
        WarningKind kind,
        WarningLevel level
) implements AlertPayload {

    @Override
    public Map<String, String> toFcmData() {
        return Map.of(
                "_srcRule", srcRule == null ? "" : srcRule,
                "kind", kind == null ? "" : kind.name(),
                "level", level == null ? "" : level.name()
        );
    }
}